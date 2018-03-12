Eclipse Git Plugin
==================

This package is licensed under the EPL.  Please refer to the COPYING
and LICENSE files for the complete licenses within each package.

This package is actually composed of three major components plus
three for packaging.

- org.eclipse.egit.core/

    An Eclipse plugin providing an interface to org.eclipse.jgit
    and support routines to allow processing against the Eclipse
    workspace and resource APIs, rather than the standard Java
    file APIs.  It also supplies the team provider implementation.

- org.eclipse.egit.ui/

    An Eclipse plugin providing the user interface on top of
    org.eclipse.egit.core.

- org.eclipse.egit.core.test/

    Unit tests for org.eclipse.egit.core.

- org.eclipse.egit/

    A plugin for packaging

- org.eclipse.egit-feature/

    Also packaging. This project is for building an Eclipse "feature"
    out of the plugins above.

- org.eclipse.egit.repository/

    This package is for producing a p2 repository, i.e. a web site
    you can point your eclipse at and just upgrade.

Warnings/Caveats
----------------

- Symbolic links are not supported because java does not support it.
  Such links could be damaged.

- Only the timestamp of the index is used by jgit check if  the index
  is dirty.

- Don't try the plugin with a JDK other than 1.6 (Java 6) unless you
  are prepared to investigate problems yourself. JDK 1.5.0_11 and later
  Java 5 versions *may* work. Earlier versions do not. JDK 1.4 is *not*
  supported. Apple's Java 1.5.0_07 is reported to work acceptably. We
  have no information about other vendors. Please report your findings
  if you try.

- CRLF conversion is never performed. On Windows you should thereforc
  make sure your projects and workspaces are configured to save files
  with Unix (LF) line endings.

Compatibility
-------------

- Eclipse 3.5.2 is the minimum Eclipse version for EGit 0.9 and later.

- Newer version of EGit may implement new functionality, remove
  existing functions and change others without other notice than what
  is written in the commit log and source files themselves.


Package Features
----------------

- org.eclipse.egit.core/

    * Supplies an Eclipse team provider.

    * Connect/disconnect the provider to a project.

    * Search for the repositories associated with a project by
      autodetecting the Git repository directories.

    * Store which repositories are tied to which containers in the
      Eclipse workspace.

    * Tracks moves/renames/deletes and reflects them in the cache
      tree.

    * Resolves through linked containers.

- org.eclipse.egit.ui/

    * Connect team provider wizard panels.

    * Connect to Git team provider by making a new repository.

    * Connect to Git team provider by searching local filesystem
      for existing repository directories.

    * Team actions: track (add), untrack (remove), disconnect, show
      history, compare version.

    * Resource decorator shows file/directory state in the package
      explorer and other views.

    * Creating new commits or amending commits.

    * Graphical history viewer with the ability to compare versions
      using eclipse built-in compare editor.

    * Clone, push, fetch

Missing Features
----------------

There are a lot of missing features. You need the real Git for this.
For some operations it may just be the preferred solution also. There
are not just a command line, there is e.g. git-gui that makes committing
partial files simple.

- Merging.

- Repacking from within the plugin.

- Generate a Git format patch.

- Apply a Git format patch.

- Documentation. :-)

- gitattributes support
  In particular CRLF conversion is not implemented. Files are treated
  as byte sequences.

- submodule support
  Submodules are not supported or even recognized.

- The Eclipse plugin cannot handle files outside any Eclipse project. You
  need commit changes to such files outside of Eclipse.

Support
-------

  Post question, comments or patches to the git@vger.kernel.org mailing list.


Contributing
------------

See SUBMITTING_PATCHES in this directory. However, feedback and bug reports
are also contributions.


About Git
---------

More information about Git, its repository format, and the canonical
C based implementation can be obtained from the Git websites:

  http://git.or.cz/
  http://www.kernel.org/pub/software/scm/git/
  http://www.kernel.org/pub/software/scm/git/docs/

