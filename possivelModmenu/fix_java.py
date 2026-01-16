import os
path = r"possivelModmenu\src\main\java\com\example\modmenu\ai\AIStateCollector.java"
c = open(path).read()
c = c.replace("\\n", "\n")
open(path, "w").write(c)
