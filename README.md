[This page](https://robtimus.github.io/whats-new-in-java/) shows all modules, packages, classes and members (constructors, fields and methods) that have been added for each Java version since Java 5.0.

## Results

The results are nested in 4 or 5 levels:
1. The Java version that has introduced the module, package, class or member.
1. Java 9 and later only: the module that was introduced, or the module of the package that was introduced, or the module of the package of the class that was introduced, or the module of the package of the enclosing class of the member that was introduced.
1. The package that was introduced, or the package of the class that was introduced, or the package of the enclosing class of the member that was introduced.
1. The class that was introduced, or the enclosing class of the member that was introduced.
1. The member that was introduced.

### Navigating results

Click on a Java version, module, package or class to toggle the visibility of its modules, packages, classes or members respectively.

If a module, package or class has a ![Java](https://robtimus.github.io/whats-new-in-java/css/img/java.png) icon after its name, this means the module, package or class itself is introduced in the given Java version. Clicking on the module, package or class will open the module, package or class in Oracle's online API.

### Filtering results

Click on _Filter_ at the top of the page to enter a filter. This filter has two modes:
* By default, the filter uses case insensitive substring matching. For example:
    * `java.util` will match not just the `java.util` package, but also other packages that start with `java.util`, as well as all classes in all of these packages.
    * `java.util.list` will match both classes `java.util.List` and `java.util.ListResourceBundle`.
* If the filter starts with `=`, the filter uses exact, case sensitive matching. For example:
    * `=java.util` will only match the `java.util` package.
    * `=java.util.list` will match nothing; you need to enter `=java.util.List` instead.

To clear an existing filter, click on the little cross icon after the filter.

Note: if a filter is in place, _Expand All_ will only expand the filter results.
