/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.analyze.model;

import static com.cburch.logisim.analyze.Strings.S;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.swing.JTextArea;

public class Implicant implements Comparable<Implicant> {
  private static class TermIterator implements Iterable<Implicant>, Iterator<Implicant> {
    final Implicant source;
    int currentMask = 0;

    TermIterator(Implicant source) {
      this.source = source;
    }

    @Override
    public boolean hasNext() {
      return currentMask >= 0;
    }

    @Override
    public Iterator<Implicant> iterator() {
      return this;
    }

    @Override
    public Implicant next() {
      final int ret = currentMask | source.values;
      final int diffs = currentMask ^ source.unknowns;
      final int diff = diffs ^ ((diffs - 1) & diffs);
      if (diff == 0) {
        currentMask = -1;
      } else {
        currentMask = (currentMask & -diff) | diff;
      }
      return new Implicant(0, ret);
    }

    @Override
    public void remove() {
      // Do nothing.
    }
  }

  private static int getNrOfOnes(int value, int nrOfBits) {
    int nrOfOnes = 0;
    int mask = 1;
    for (int bitIndex = 0; bitIndex < nrOfBits; bitIndex++) {
      if ((value & mask) != 0) nrOfOnes++;
      mask <<= 1;
    }
    return nrOfOnes;
  }

  private static void report(JTextArea out, String info) {
    if (out != null) out.append(info);
  }

  private static String getGroupRepresentation(int value, int dontCares, int nrOfBits) {
    final java.lang.StringBuffer result = new StringBuffer();
    int mask = 1 << (nrOfBits - 1);
    while (mask > 0) {
      if ((dontCares & mask) != 0) {
        result.append("-");
      } else {
        result.append((value & mask) != 0 ? "1" : "0");
      }
      mask >>= 1;
    }
    return result.toString();
  }

  static List<Implicant> computeMinimal(int format, AnalyzerModel model, String variable, JTextArea outputArea) {
    final com.cburch.logisim.analyze.model.TruthTable table = model.getTruthTable();
    final int outputVariableIndex = model.getOutputs().bits.indexOf(variable);
    if (outputVariableIndex < 0) return Collections.emptyList();
    // first we do some house keeping
    final com.cburch.logisim.analyze.model.Entry desiredTerm = format == AnalyzerModel.FORMAT_SUM_OF_PRODUCTS ? Entry.ONE : Entry.ZERO;
    final com.cburch.logisim.analyze.model.Entry skippedTerm = desiredTerm == Entry.ONE ? Entry.ZERO : Entry.ONE;
    final int nrOfInputs = table.getInputColumnCount();
    final java.util.HashSet<java.lang.Integer> oneHotTable = new HashSet<Integer>();
    int mask = 1;
    for (int bitIndex = 0; bitIndex < nrOfInputs; bitIndex++) {
      oneHotTable.add(mask);
      mask <<= 1;
    }

    // Here we define the first table with all desired terms (minterms or maxterms) and we add also the don´t cares
    // for the primes we have the group (key) and the min/maxterms in this group (HashSet)
    final java.util.HashMap<com.cburch.logisim.analyze.model.Implicant,java.util.HashSet<com.cburch.logisim.analyze.model.Implicant>> primes = new HashMap<Implicant, HashSet<Implicant>>();
    final java.util.ArrayList<com.cburch.logisim.analyze.model.Implicant> essentialPrimes = new ArrayList<Implicant>();
    // for the currentTable and the nextTable the key is the number of ones in the min/maxterms (groupid)
    // the HashMap keeps track of the group (key) implicant and the number of min/max terms in this group (HashSet)
    final java.util.HashMap<java.lang.Integer,java.util.HashMap<com.cburch.logisim.analyze.model.Implicant,java.util.HashSet<com.cburch.logisim.analyze.model.Implicant>>> currentTable = new HashMap<Integer, HashMap<Implicant, HashSet<Implicant>>>();
    final java.util.HashMap<java.lang.Integer,java.util.HashMap<com.cburch.logisim.analyze.model.Implicant,java.util.HashSet<com.cburch.logisim.analyze.model.Implicant>>> newTable = new HashMap<Integer, HashMap<Implicant, HashSet<Implicant>>>();
    // for terms to cover is the "key" the min/maxterms that need to be covered, and the ArrayList 
    // the set of prime covers that cover the key
    final java.util.HashMap<com.cburch.logisim.analyze.model.Implicant,java.util.ArrayList<com.cburch.logisim.analyze.model.Implicant>> termsToCover = new HashMap<Implicant, ArrayList<Implicant>>();
    boolean allDontCare = true;
    for (int inputCombination = 0; inputCombination < table.getRowCount(); inputCombination++) {
      final com.cburch.logisim.analyze.model.Entry term = table.getOutputEntry(inputCombination, outputVariableIndex);
      if (term == skippedTerm) {
        allDontCare = false;
        continue;
      }
      final int nrOfOnes = getNrOfOnes(inputCombination, nrOfInputs);
      final boolean isDontCare = term != desiredTerm;
      final com.cburch.logisim.analyze.model.Implicant implicant = new Implicant(inputCombination, isDontCare);
      final java.util.HashSet<com.cburch.logisim.analyze.model.Implicant> implicantsSet = new HashSet<Implicant>();
      if (!isDontCare) {
        termsToCover.put(implicant, new ArrayList<>());
        implicantsSet.add(implicant);
        allDontCare = false;
      }
      if (!newTable.containsKey(nrOfOnes)) {
        newTable.put(nrOfOnes, new HashMap<>());
      }
      newTable.get(nrOfOnes).put(implicant, implicantsSet);
    }

    if (allDontCare) return Collections.emptyList();
    // In case the number of inputs is bigger than approx. 8 inputs, this
    // algorithm takes a long time. To prevent "freezing" of logisim, we
    // only perform an optimization for systems with more than 6 inputs on
    // user request. Otherwise we exit here and return the set of min/maxterms
    if ((nrOfInputs > MAXIMAL_NR_OF_INPUTS_FOR_AUTO_MINIMAL_FORM) && (outputArea == null)) {
      return Collections.emptyList();
    }
    report(outputArea, String.format("\n%s\n", S.fmt("implicantOutputName", variable)));
    // Here the real work starts, we determine all primes
    boolean couldMerge = false;
    int groupSize = 2;
    do {
      report(outputArea, String.format("\n%s", S.fmt("implicantGroupSize", groupSize)));
      long nrOfPrimes = 0L;
      couldMerge = false;
      currentTable.clear();
      currentTable.putAll(newTable);
      newTable.clear();
      int minimalKey = Integer.MAX_VALUE;
      int maximalKey = 0;
      for (java.lang.Integer key : currentTable.keySet()) {
        if (key < minimalKey) minimalKey = key;
        if (key > maximalKey) maximalKey = key;
      }
      for (int key = minimalKey; key < maximalKey; key++) {
        if (currentTable.containsKey(key) && currentTable.containsKey(key + 1)) {
          // we see if we can merge terms
          for (com.cburch.logisim.analyze.model.Implicant termGroup1 : currentTable.get(key).keySet()) {
            for (com.cburch.logisim.analyze.model.Implicant termGroup2 : currentTable.get(key + 1).keySet()) {
              if (termGroup1.unknowns != termGroup2.unknowns) continue;
              final int differenceMask = termGroup1.values ^ termGroup2.values;
              if (oneHotTable.contains(differenceMask)) {
                final int dontCareMask = termGroup1.unknowns | differenceMask;
                final int newValue = (termGroup1.values & differenceMask) == 0 ? termGroup1.values : termGroup2.values;
                final boolean isDontCareGroup = termGroup1.isDontCare && termGroup2.isDontCare;
                final com.cburch.logisim.analyze.model.Implicant newImplicant = new Implicant(dontCareMask, newValue, isDontCareGroup);
                final java.util.HashSet<com.cburch.logisim.analyze.model.Implicant> newImplicantTerms = new HashSet<Implicant>();
                couldMerge = true;
                termGroup1.isPrime = termGroup2.isPrime = false;
                newImplicantTerms.addAll(currentTable.get(key).get(termGroup1));
                newImplicantTerms.addAll(currentTable.get(key + 1).get(termGroup2));
                boolean found = false;
                if (newTable.containsKey(key)) {
                  // see if the new implicant already is in the set
                  for (final com.cburch.logisim.analyze.model.Implicant implicant : newTable.get(key).keySet()) {
                    found |= (implicant.values == newValue) && (implicant.unknowns == dontCareMask);
                  }
                  if (!found) newTable.get(key).put(newImplicant, newImplicantTerms);
                } else {
                  newTable.put(key, new HashMap<>());
                  newTable.get(key).put(newImplicant, newImplicantTerms);
                }
              }
            }
          } 
        }
      }
      // now we add the primes to the set
      for (final java.lang.Integer key : currentTable.keySet()) {
        for (final com.cburch.logisim.analyze.model.Implicant implicant : currentTable.get(key).keySet()) {
          if (implicant.isPrime && !implicant.isDontCare) {
            primes.put(implicant, currentTable.get(key).get(implicant));
            if ((nrOfPrimes % 16L) == 0L) report(outputArea, "\n");
            report(outputArea, String.format("%s ", 
                  getGroupRepresentation(implicant.values, implicant.unknowns, nrOfInputs)));
            nrOfPrimes++;
          }
        }
      }
      if (nrOfPrimes == 0) report(outputArea, String.format("\n%s", S.get("implicantNoneFound")));
      groupSize <<= 1;
    } while (couldMerge);

    // we build now the table, with for each term which prime it covers
    for (final com.cburch.logisim.analyze.model.Implicant prime : primes.keySet()) {
      for (final com.cburch.logisim.analyze.model.Implicant term : termsToCover.keySet()) {
        if (primes.get(prime).contains(term)) termsToCover.get(term).add(prime);
      }
    }
    // finally we have to find the essential primes
    boolean couldDoRowReduction = false;
    boolean couldDoColumnReduction = false;

    report(outputArea, String.format("\n%s", S.get("implicantColumRowReduction")));
    long nrEssentialPrimes = 0L;
    do {
      couldDoRowReduction = false;
      couldDoColumnReduction = false;
      final java.util.ArrayList<com.cburch.logisim.analyze.model.Implicant> termsToRemove = new ArrayList<Implicant>();
      // we first try a column reduction
      for (final com.cburch.logisim.analyze.model.Implicant term : termsToCover.keySet()) {
        final java.util.ArrayList<com.cburch.logisim.analyze.model.Implicant> termInfo = termsToCover.get(term);
        if (termInfo.size() == 1) {
          // we found a prime cover, as this cover only covers this term
          final com.cburch.logisim.analyze.model.Implicant prime = termInfo.get(0);
          if (!primes.containsKey(prime)) continue;
          for (final com.cburch.logisim.analyze.model.Implicant terms : primes.get(prime)) {
            for (final com.cburch.logisim.analyze.model.Implicant currentPrime : primes.keySet()) {
              if (currentPrime.equals(prime)) continue;
              couldDoColumnReduction |= primes.get(currentPrime).contains(terms);
              primes.get(currentPrime).remove(terms);
            }
            termsToRemove.add(terms);
          }
          essentialPrimes.add(prime);
          primes.remove(prime);
          if ((nrEssentialPrimes++ % 16L) == 0) report(outputArea, "\n");
          report(outputArea, String.format(" %s", getGroupRepresentation(prime.values, prime.unknowns, nrOfInputs)));
        }
      }
      // we do the cleanup
      for (final com.cburch.logisim.analyze.model.Implicant term : termsToRemove) termsToCover.remove(term);
      // now we perform the row reduction
      // first we look for empty primes
      final java.util.HashSet<com.cburch.logisim.analyze.model.Implicant> primesToRemove = new HashSet<Implicant>();
      final java.util.HashMap<java.lang.Integer,java.util.HashSet<com.cburch.logisim.analyze.model.Implicant>> primeHierarchy = new HashMap<Integer, HashSet<Implicant>>();
      final java.util.ArrayList<java.lang.Integer> nrOfElementGroups = new ArrayList<Integer>();
      for (final com.cburch.logisim.analyze.model.Implicant prime : primes.keySet()) {
        final java.util.HashSet<com.cburch.logisim.analyze.model.Implicant> primeElements = primes.get(prime);
        if (primeElements.isEmpty()) {
          primesToRemove.add(prime);
          couldDoRowReduction = true;
        } else {
          final int nrOfElements = primeElements.size();
          if (!primeHierarchy.containsKey(nrOfElements)) {
            primeHierarchy.put(nrOfElements, new HashSet<>());
          }
          primeHierarchy.get(nrOfElements).add(prime);
          if (!nrOfElementGroups.contains(nrOfElements)) {
            nrOfElementGroups.add(nrOfElements);
          }
        }
      }
      Collections.sort(nrOfElementGroups);
      if (!nrOfElementGroups.isEmpty()) {
        for (int mergeGroupId = nrOfElementGroups.size() - 1; mergeGroupId > 0; mergeGroupId--) {
          for (final com.cburch.logisim.analyze.model.Implicant bigPrime : primeHierarchy.get(nrOfElementGroups.get(mergeGroupId))) {
            if (primesToRemove.contains(bigPrime)) continue;
            for (int checkGroupId = mergeGroupId - 1; checkGroupId >= 0; checkGroupId--) {
              for (final com.cburch.logisim.analyze.model.Implicant smallPrime : primeHierarchy.get(nrOfElementGroups.get(checkGroupId))) {
                if (primesToRemove.contains(smallPrime)) continue;
                if (primes.get(bigPrime).containsAll(primes.get(smallPrime))) {
                  couldDoRowReduction = true;
                  primesToRemove.add(smallPrime);
                }
              }
            }
          }
        }
      }
      for (final com.cburch.logisim.analyze.model.Implicant prime : primesToRemove) {
        primes.remove(prime);
        for (final com.cburch.logisim.analyze.model.Implicant element : termsToCover.keySet()) termsToCover.get(element).remove(prime);
      }
    } while (couldDoRowReduction || couldDoColumnReduction);
    // It can happen that we still have max/min terms left that are covered by multiple groups.
    // To find the minimal cover here we should implement the Petrick's method (https://en.wikipedia.org/wiki/Petrick%27s_method)
    // For the moment we are just going to greedyly pick
    // TODO: Implement Petrick's method
    if (!termsToCover.isEmpty()) report(outputArea, String.format("\n\n\s", S.get("implicantGreedy")));
    nrEssentialPrimes = 0L;
    while (!termsToCover.isEmpty()) {
      final java.util.ArrayList<com.cburch.logisim.analyze.model.Implicant> termsToRemove = new ArrayList<Implicant>();
      for (final com.cburch.logisim.analyze.model.Implicant term : termsToCover.keySet()) {
        if (termsToRemove.contains(term)) continue;
        final com.cburch.logisim.analyze.model.Implicant group = termsToCover.get(term).get(0);
        essentialPrimes.add(group);
        if ((nrEssentialPrimes++ % 16L) == 0) report(outputArea, "\n");
        report(outputArea, String.format(" %s", getGroupRepresentation(group.values, group.unknowns, nrOfInputs)));
        termsToRemove.addAll(primes.get(group));
      }
      for (final com.cburch.logisim.analyze.model.Implicant term : termsToRemove) termsToCover.remove(term);
    }

    // TODO: Return multiple minimal covers if present (Petrick's method)
    return essentialPrimes;
  }

  public static Expression toExpression(int format, AnalyzerModel model, List<Implicant> implicants) {
    if (implicants == null) return null;
    final com.cburch.logisim.analyze.model.TruthTable table = model.getTruthTable();
    if (format == AnalyzerModel.FORMAT_PRODUCT_OF_SUMS) {
      Expression product = null;
      for (final com.cburch.logisim.analyze.model.Implicant implicant : implicants) {
        product = Expressions.and(product, implicant.toSum(table));
      }
      return product == null ? Expressions.constant(1) : product;
    } else {
      Expression sum = null;
      for (final com.cburch.logisim.analyze.model.Implicant implicant : implicants) {
        sum = Expressions.or(sum, implicant.toProduct(table));
      }
      return sum == null ? Expressions.constant(0) : sum;
    }
  }

  static final Implicant MINIMAL_IMPLICANT = new Implicant(0, -1);
  static final List<Implicant> MINIMAL_LIST = Collections.singletonList(MINIMAL_IMPLICANT);
  public static final int MAXIMAL_NR_OF_INPUTS_FOR_AUTO_MINIMAL_FORM = 6;

  final int unknowns;
  final int values;
  final boolean isDontCare;
  boolean isPrime = true;

  private Implicant(int unknowns, int values) {
    this.unknowns = unknowns;
    this.values = values;
    isDontCare = false;
  }

  private Implicant(int unknowns, int values, boolean dontCareTerm) {
    this.unknowns = unknowns;
    this.values = values;
    isDontCare = dontCareTerm;
  }

  private Implicant(int values, boolean dontCareTerm) {
    this.unknowns = 0;
    this.values = values;
    isDontCare = dontCareTerm;
  }

  @Override
  public int compareTo(Implicant o) {
    if (this.values < o.values) return -1;
    if (this.values > o.values) return 1;
    return Integer.compare(this.unknowns, o.unknowns);
  }

  @Override
  public boolean equals(Object other) {
    return (other instanceof Implicant o)
           ? this.unknowns == o.unknowns && this.values == o.values
           : false;
  }

  public int getRow() {
    return (unknowns != 0) ? -1 : values;
  }

  public Iterable<Implicant> getTerms() {
    return new TermIterator(this);
  }

  public int getUnknownCount() {
    int ret = 0;
    int n = unknowns;
    while (n != 0) {
      n &= (n - 1);
      ret++;
    }
    return ret;
  }

  @Override
  public int hashCode() {
    return (unknowns << 16) | values;
  }

  public Expression toProduct(TruthTable source) {
    Expression term = null;
    final int cols = source.getInputColumnCount();
    for (int i = cols - 1; i >= 0; i--) {
      if ((unknowns & (1 << i)) == 0) {
        com.cburch.logisim.analyze.model.Expression literal = Expressions.variable(source.getInputHeader(cols - 1 - i));
        if ((values & (1 << i)) == 0) literal = Expressions.not(literal);
        term = Expressions.and(term, literal);
      }
    }
    return term == null ? Expressions.constant(1) : term;
  }

  public Expression toSum(TruthTable source) {
    Expression term = null;
    final int cols = source.getInputColumnCount();
    for (int i = cols - 1; i >= 0; i--) {
      if ((unknowns & (1 << i)) == 0) {
        com.cburch.logisim.analyze.model.Expression literal = Expressions.variable(source.getInputHeader(cols - 1 - i));
        if ((values & (1 << i)) != 0) literal = Expressions.not(literal);
        term = Expressions.or(term, literal);
      }
    }
    return term == null ? Expressions.constant(1) : term;
  }

  static SortedMap<Implicant, String> computePartition(AnalyzerModel model) {
    // The goal is to find a minimal partitioning of each of the regions (of
    // the {0,1}^n hypercube) defined by the truth table output entries
    // (string of zero, one, dont_care, error, etc.). Similar to K-maps, we
    // can fairly easily find all the prime implicants, but unlike K-maps,
    // we can't have overlap, so a different algorithm is called for. Maybe
    // something from set-covering or binary-partition-trees? It's not even
    // obvious what the complexity of this problem is. We'll just go with a
    // simple greedy algorithm and hope for the best: sort the prime
    // implicants, keep accepting non-overlapping ones until we have covered
    // the region.
    final com.cburch.logisim.analyze.model.TruthTable table = model.getTruthTable();
    final int maxval = (1 << table.getInputColumnCount()) - 1;
    // Determine the set of regions and the first-cut implicants for each
    // region.
    final java.util.HashMap<java.lang.String,java.util.HashSet<com.cburch.logisim.analyze.model.Implicant>> regions = new HashMap<String, HashSet<Implicant>>();
    for (int i = 0; i < table.getVisibleRowCount(); i++) {
      final java.lang.String val = table.getVisibleOutputs(i);
      final int idx = table.getVisibleRowIndex(i);
      final int dc = table.getVisibleRowDcMask(i);
      final com.cburch.logisim.analyze.model.Implicant imp = new Implicant(dc, idx);
      final java.util.HashSet<com.cburch.logisim.analyze.model.Implicant> region = regions.computeIfAbsent(val, k -> new HashSet<>());
      region.add(imp);
    }
    // For each region...
    final java.util.TreeMap<com.cburch.logisim.analyze.model.Implicant,java.lang.String> ret = new TreeMap<Implicant, String>();
    for (java.util.Map.Entry<java.lang.String,java.util.HashSet<com.cburch.logisim.analyze.model.Implicant>> it : regions.entrySet()) {
      final java.lang.String val = it.getKey();
      final java.util.HashSet<com.cburch.logisim.analyze.model.Implicant> base = it.getValue();

      // Work up to more general implicants.
      final java.util.HashSet<com.cburch.logisim.analyze.model.Implicant> all = new HashSet<Implicant>();
      java.util.HashSet<com.cburch.logisim.analyze.model.Implicant> current = base;
      while (!current.isEmpty()) {
        final java.util.HashSet<com.cburch.logisim.analyze.model.Implicant> next = new HashSet<Implicant>();
        for (final com.cburch.logisim.analyze.model.Implicant implicant : current) {
          all.add(implicant);
          for (int j = 1; j <= maxval; j *= 2) {
            if ((implicant.unknowns & j) != 0) continue;
            final com.cburch.logisim.analyze.model.Implicant opp = new Implicant(implicant.unknowns, implicant.values ^ j);
            if (!all.contains(opp)) continue;
            final com.cburch.logisim.analyze.model.Implicant i = new Implicant(opp.unknowns | j, opp.values);
            next.add(i);
          }
        }
        current = next;
      }

      final java.util.ArrayList<com.cburch.logisim.analyze.model.Implicant> sorted = new ArrayList<>(all);
      sorted.sort(sortByGenerality);
      final java.util.ArrayList<com.cburch.logisim.analyze.model.Implicant> chosen = new ArrayList<Implicant>();
      for (final com.cburch.logisim.analyze.model.Implicant implicant : sorted) {
        if (disjoint(implicant, chosen)) {
          chosen.add(implicant);
          ret.put(implicant, val);
        }
      }
    }

    // TODO in caller: convert implicant to Row and val back to Entry[]
    return ret;
  }

  private static boolean disjoint(Implicant imp, ArrayList<Implicant> chosen) {
    for (final com.cburch.logisim.analyze.model.Implicant other : chosen) {
      final int dc = imp.unknowns | other.unknowns;
      if ((imp.values & ~dc) == (other.values & ~dc)) return false;
    }
    return true;
  }

  private static final CompareGenerality sortByGenerality = new CompareGenerality();

  private static class CompareGenerality implements Comparator<Implicant> {
    @Override
    public int compare(Implicant i1, Implicant i2) {
      final int diff = (i2.getUnknownCount() - i1.getUnknownCount());
      if (diff != 0) return diff;
      return (i1.values & ~i1.unknowns) - (i2.values & ~i2.unknowns);
    }
  }
}
