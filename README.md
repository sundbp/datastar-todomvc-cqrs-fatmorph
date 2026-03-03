# Demo of datastar using a todo app

This repo shows todomvc using a pattern of using datastar, CQRS and fat morph.

It contains my home grown state and REPL approach (heavily inspired by:
[https://medium.com/@maciekszajna/reloaded-workflow-out-of-the-box-be6b5f38ea98](Reloaded workflow out of the box)

To run the example:

```bash
bb dev # starts the repl
# in the REPL run (reset)
```

A `(reset)` will recompile any namespaces affected by changes since the last reset, and restart the stateful system.

You can see the web UI at: [http://127.0.0.1:8899](http://127.0.0.1:8899)
