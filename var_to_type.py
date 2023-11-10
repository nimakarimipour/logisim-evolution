## open out.txt
## read each line
## split each line by space
with open('out.txt') as f:
    lines = f.readlines()
    # reverse lines
    lines.reverse()
    for l in lines:
        start = l.find(" ")
        end = l.find("/Users/nima/Developer/logisim-evolution/src/main/java/")
        offset = int(l[:start]) - 4
        type = l[start + 1:end - 1]
        path = l[end:][:-1]
        with open(path) as f:
            ## read f as one string
            content = f.read()
            if content[offset:offset+ 4] != "var ":
                raise Exception("var not found")
            content = content[:offset] + type + " " + content[offset + 4:]
            with open(path, "w") as f:
                f.write(content)
            
