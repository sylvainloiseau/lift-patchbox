# Add command for merge

# Search functionality

I would like to add a "search" functionality, that would use the rich semantics of the language to express searches.

This command will take a selector chain (a path) as input. The selector chain is a chain of selectors separated by "within" or "of" clauses. There is no "under" clause.

For instance :

```
search example[text ~ "pig"] within sense[gloss@de="Schwein"] within entry[form="mami"]
```

This command will return the set of same-kind components matching the path, refererred with their UUID, available on very  components in the lift-api library.

Contrary to other existing commands, the selector chain does not have to resolve to a single component. It can return a set.

Contrary to other existing commands, it has then no side-effect.

Therefore, this command will not be usefull in a script. It will be useful with the lift-patchbox library rather than by itself, for instance to search for all entries containing a given word. Maybe a command verb proper is not necessary, and it can be implemented only by adding a method to the library.

In order to be useful, I think several things need to be added. For instance, an empty predicate, that will matches all nodes, could be denoted with a star:

```
search example[text ~ "pig"] within sense[gloss@de="Schwein"] within entry[*]
```

Or the possibility to skip an ancestor, for instance to refer to field wathever the depth, thanks to the `descending` keyword:

```
search example[text ~ "pig"] descending sense[gloss@de="Schwein"] within entry[*]
```

match-any predicate such as `entry[*]` is deliberately **not** part of version
1.0; should a future version introduce one, for a query command rather than a
mutation command, it would be a filtering construct and `INCOMPLETE_SELECTOR`
would be its verdict in a unique selector.)

# Add an RPL

Could you add an RPL interface at the command line?

# Done

## Add command for managing the dictionary featureset

After 2.1.3, add a new 2.1.4 section headed "Managing dictionary feature and feature sets". Along the line of directive for managing language and creating languages, commands / directive should be available to manage the feature sets, and features of a dictionary. It could be something like:

lift-dictionary fs create "name"
lift-dictionary fs delete "name"
lift-dictionary fs rename "name"
lift-dictionary f create "f_name" on "fs_name"
lift-dictionary f delete "f_name" on "fs_name"
lift-dictionary f rename "f_name" on "fs_name" as "new_name"

However please offer better syntax. It should also be possible to delete language and to create new field and new trait, specifying the name and the data model. Please provide suggestions for all of this, as well as the necessary addition to the specification, the antlr parser, and the java code.
