# Basic Idea

Right now, this is basically a project template
that's set up in a way (and using libraries) that
make sense to me. You may not appreciate this
approach at all.

# Main Points

## Server Side

    boot cider interact

Starts up a REPL at the command-line. Attach to this using CIDER
to do interesting things.

### Interesting Things

Starts with

    (require 'user)
    (in-ns 'user)
    (prep)
    (init)

Now you should have a web server that's up and running and ready to
work with the client side.

## Client Side

    boot cider dev

This should set up a file system watcher that's ready to interact.

## Load it up

Point your browser at
[http://localhost:8002/index.html](http://localhost:8002/index.html)
and watch the magic begin.
