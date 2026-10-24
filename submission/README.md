# Group 27 Phase 3b.9 Preparation

The current prepared artifacts are a runnable JAR and an editable DOCX manual. No submission ZIP is created yet. The JAR targets Java 21 and later. Building requires a JDK 21+ and g++ for the native execution tests.

Run `./submission/build-submission.sh` from the repository root to compile production and test sources for Java 21, run every test class, and build `submission/group-27.jar`. The script does not create an archive.

The JAR accepts input, XML-output, and generated-C++-output paths:

```text
java -jar group-27.jar input.spl tree.xml program.cpp
```

Run it from the `submission` folder, or provide a path to the JAR:

```text
java -jar submission/group-27.jar input.spl tree.xml program.cpp
```

The generated target is human-readable C++. Compile it with `g++ -std=c++11 program.cpp -o program`; with gcc, explicitly link the C++ standard library using `gcc -std=c++11 program.cpp -lstdc++ -o program`.


## Phase 3b.9 verification record

The complete regression loop passed all 24 test classes on 2026-10-24. Native C++ execution ran with g++ 15.2.0; the inline-call execution suites each report 17 passing programs, and the Phase 3a end-to-end suite reports 45 native cases. The runnable JAR was smoke-tested through its full CLI, and the resulting program printed `hello` when compiled and run with both g++ 15.2.0 and gcc 15.2.0 (gcc used `-lstdc++`). The JAR is compiled with `javac --release 21`; the local build JDK was 25.

Supported CLI modes:

- `java -jar group-27.jar`: default input and XML paths; no target file.
- `java -jar group-27.jar input.spl tree.xml`: explicit input and XML paths; no target file.
- `java -jar group-27.jar input.spl tree.xml program.cpp`: full Phase 3 translation unit.

The numeric backend emits `int` for WHOLE mode and `float` for REAL mode. The suite exercises both modes, argument binding, nested inlining, label generation, control flow, and frontend/semantic regressions. The 3b sheet's apparent `pN = aN` versus implementation's `aN = pN` direction remains a tutor clarification; executable argument binding currently copies each captured actual into its formal.
