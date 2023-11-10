import subprocess
import os
import shutil
from pathlib import Path

VERSION = '1.3.9-SNAPSHOT'
REPO = subprocess.check_output(['git', 'rev-parse', '--show-toplevel']).strip().decode('utf-8')
OUT_DIR = '{}/annotator-out/'.format(REPO)
ANNOTATOR_JAR = "{}/.m2/repository/edu/ucr/cs/riple/annotator/annotator-core/{}/annotator-core-{}.jar".format(str(Path.home()), VERSION, VERSION)


def prepare():
    os.makedirs(OUT_DIR, exist_ok=True)
    shutil.rmtree('{}/0'.format(OUT_DIR), ignore_errors=True)
    with open('{}/paths.tsv'.format(OUT_DIR), 'w') as o:
        o.write("{}\t{}\n".format('{}/checker.xml'.format(OUT_DIR), '{}/scanner.xml'.format(OUT_DIR)))


def run_annotator():
    prepare()
    commands = []
    commands += ["java", "-jar", ANNOTATOR_JAR]
    commands += ['-d', OUT_DIR]
    commands += ['-bc', 'cd {} && ./gradlew compileJava'.format(REPO)]
    commands += ['-cp', '{}/paths.tsv'.format(OUT_DIR)]
    commands += ['-i', 'edu.ucr.Initializer']
    commands += ['-n', 'edu.ucr.cs.riple.taint.ucrtainting.qual.RUntainted']
    commands += ['-cn', 'UCRTaint']
    commands += ["--depth", "5"]
    # Uncomment to see build output
    # commands += ['-rboserr']
    # Uncomment to disable outer loop
    # commands += ['-dol']
    # Uncomment to disable parallel processing
    # commands += ['--disable-parallel-processing']

    subprocess.call(commands)


run_annotator()
