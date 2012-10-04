/* NSC -- new Scala compiler
 * Copyright 2005-2012 LAMP/EPFL
 * @author  Martin Odersky
 */
package scala.reflect
package api

/** A slice of [[scala.reflect.api.Universe the Scala reflection cake]] that defines trees and operations on them.
 *  See [[scala.reflect.api.Universe]] for a description of how the reflection API is encoded with the cake pattern.
 *
 *  Tree is the basis for scala's abstract syntax. The nodes are
 *  implemented as case classes, and the parameters which initialize
 *  a given tree are immutable: however trees have several mutable
 *  fields which are manipulated in the course of typechecking,
 *  including `pos`, `symbol`, and `tpe`.
 *
 *  Newly instantiated trees have `tpe` set to null (though it
 *  may be set immediately thereafter depending on how it is
 *  constructed.) When a tree is passed to the typechecker
 *  (via toolboxes in runtime reflection or using
 *  [[scala.reflect.macros.Context#typeCheck]] in comple-time reflection)
 *  under normal circumstances the `tpe` must be
 *  `null` or the typechecker will ignore it. Furthermore, the typechecker is not
 *  required to return the same tree it was passed.
 *
 *  Trees can be easily traversed with e.g. `foreach` on the root node;
 *  for a more nuanced traversal, subclass `Traverser`. Transformations
 *  are done by subclassing `Transformer`.
 *
 *  Copying Trees should be done with care depending on whether
 *  it needs be done lazily or strictly (see [[scala.reflect.api.Trees#newLazyTreeCopier]] and
 *  [[scala.reflect.api.Trees#newStrictTreeCopier]]) and on whether the contents of the mutable
 *  fields should be copied. The tree copiers will copy the mutable
 *  attributes to the new tree. A shortcut way of copying trees is [[scala.reflect.api.Trees#Tree#duplicate]]
 *  which uses a strict copier.
 *
 *  Trees can be coarsely divided into four mutually exclusive categories:
 *
 *  - Subclasses of `TermTree`, representing terms
 *  - Subclasses of `TypTree`, representing types.  Note that is `TypTree`, not `TypeTree`.
 *  - Subclasses of `SymTree`, which either define or reference symbols.
 *  - Other trees, which have none of those as superclasses.
 *
 *  `SymTrees` include important nodes `Ident` (which represent references to identifiers)
 *  and `Select` (which represent member selection). These nodes can be used as both terms and types;
 *  they are distinguishable based on whether their underlying [[scala.reflect.api.Names#Name]]
 *  is a `TermName` or `TypeName`.  The correct way to test any Tree for a type or a term are the `isTerm`/`isType`
 *  methods on Tree.
 *
 *  "Others" are mostly syntactic or short-lived constructs. Take, for example,
 *  `CaseDef`, which wraps individual match cases: such nodes are neither terms nor types,
 *  nor do they carry a symbol.
 *
 *  === How to get a tree that corresponds to a snippet of Scala code? ===
 *
 *  With the introduction of compile-time metaprogramming and runtime compilation in Scala 2.10.0,
 *  quite often it becomes necessary to convert Scala code into corresponding trees.
 *
 *  The simplest was to do that is to use [[scala.reflect.api.Universe#reify]].
 *  The `reify` method takes an valid Scala expression (i.e. it has to be well-formed
 *  with respect to syntax and has to typecheck, which means no unresolved free variables).
 *  and produces a tree that represents the input.
 *
 *  {{{
 *  scala> import scala.reflect.runtime.universe._
 *  import scala.reflect.runtime.universe._
 *
 *  // trying to reify a snippet that doesn't typecheck
 *  // leads to a compilation error
 *  scala> reify(x + 2)
 *  <console>:31: error: not found: value x
 *                reify(x + 2)
 *                      ^
 *
 *  scala> val x = 2
 *  x: Int = 2
 *
 *  // now when the variable x is in the scope
 *  // we can successfully reify the expression `x + 2`
 *  scala> val expr = reify(x + 2)
 *  expr: reflect.runtime.universe.Expr[Int] = Expr[Int](x.$plus(2))
 *
 *  // the result of reification is of type Expr
 *  // exprs are thin wrappers over trees
 *  scala> expr.tree
 *  res2: reflect.runtime.universe.Tree = x.$plus(2)
 *
 *  // we can see that the expression `x + 2`
 *  // is internally represented as an instance of the `Apply` case class
 *  scala> res2.getClass.toString
 *  res3: String = class scala.reflect.internal.Trees$Apply
 *
 *  // when it comes to inspecting the structure of the trees,
 *  // the default implementation of `toString` doesn't help much
 *  // the solution is discussed in one of the next sections
 *  }}}
 *
 *  The alternative way of getting an AST of a snippet of Scala code
 *  is having it parsed by a toolbox (see [[scala.reflect.api.package the overview page]]
 *  for more information about toolboxes):
 *  {{{
 *  scala> import scala.reflect.runtime.universe._
 *  import scala.reflect.runtime.universe._
 *
 *  scala> import scala.reflect.runtime.{currentMirror => cm}
 *  import scala.reflect.runtime.{currentMirror=>cm}
 *
 *  scala> import scala.tools.reflect.ToolBox // requires scala-compiler.jar
 *  import scala.tools.reflect.ToolBox
 *
 *  scala> val tb = cm.mkToolBox()
 *  tb: scala.tools.reflect.ToolBox[reflect.runtime.universe.type] = ...
 *
 *  scala> tb.parse("x + 2")
 *  res0: tb.u.Tree = x.$plus(2)
 *  }}}
 *
 *  === How to evaluate a tree? ===
 *
 *  Once there's a way to get a tree that represents Scala code, the next question
 *  is how to evaluate it. The answer to this question depends on what flavor of reflection is used:
 *  runtime reflection or compile-time reflection (macros).
 *
 *  Within runtime reflection, evaluation can be carried out using toolboxes.
 *  To create a toolbox one wraps a classloader in a mirror and then uses the mirror
 *  to instantiate a toolbox. Later on the underlying classloader will be used to map
 *  symbolic names (such as `List`) to underlying classes of the platform
 *  (see [[scala.reflect.api.package the overview page]] for more information about universes,
 *  mirrors and toolboxes):
 *
 *  {{{
 *  scala> import scala.reflect.runtime.universe._
 *  import scala.reflect.runtime.universe._
 *
 *  scala> import scala.tools.reflect.ToolBox // requires scala-compiler.jar
 *  import scala.tools.reflect.ToolBox
 *
 *  scala> val mirror = runtimeMirror(getClass.getClassLoader)
 *  mirror: reflect.runtime.universe.Mirror = JavaMirror with ...
 *
 *  scala> val tb = mirror.mkToolBox()
 *  tb: scala.tools.reflect.ToolBox[reflect.runtime.universe.type] = ...
 *
 *  scala> tb.eval(tb.parse("2 + 2"))
 *  res0: Int = 4
 *  }}}
 *
 *  At compile-time, [[scala.reflect.macros.Context]] provides the [[scala.reflect.macros.Evals#eval]] method,
 *  which doesn't require manual instantiation of mirrors and toolboxes and potentially will have better performance
 *  (at the moment it still creates toolboxes under the cover, but in later releases it might be optimized
 *  to reuse the infrastructure of already running compiler).
 *
 *  Behind the scenes tree evaluation launches the entire compilation pipeline and creates an in-memory virtual directory
 *  that holds the resulting class files (that's why it requires scala-compiler.jar when used with runtime reflection).
 *  This means that the tree being evaluated should be valid Scala code (e.g. it shouldn't contain type errors).
 *
 *  Quite often though there is a need to evaluate code in some predefined context. For example, one might want to use a dictionary
 *  that maps names to values as an environment for the code being evaluated. This isn't supported out of the box,
 *  but nevertheless this scenario is possible to implement. See a [[http://stackoverflow.com/questions/12122939 Stack Overflow topic]]
 *  for more details.
 *
 *  === How to get an internal representation of a tree? ===
 *
 *  The `toString` method on trees is designed to print a close-to-Scala representation
 *  of the code that a given tree represents. This is usually convenient, but sometimes
 *  one would like to look under the covers and see what exactly are the AST nodes that
 *  constitute a certain tree.
 *
 *  Scala reflection provides a way to dig deeper through [[scala.reflect.api.Printers]]
 *  and their `showRaw` method. Refer to the page linked above for a series of detailed
 *  examples.
 *
 *  {{{
 *  scala> import scala.reflect.runtime.universe._
 *  import scala.reflect.runtime.universe._
 *
 *  scala> def tree = reify{ final class C { def x = 2 } }.tree
 *  tree: reflect.runtime.universe.Tree
 *
 *  // show displays prettified representation of reflection artifacts
 *  // which is typically close to Scala code, but sometimes not quite
 *  // (e.g. here the constructor is shown in a desugared way)
 *  scala> show(tree)
 *  res0: String =
 *  {
 *    final class C extends AnyRef {
 *      def <init>() = {
 *        super.<init>();
 *        ()
 *      };
 *      def x = 2
 *    };
 *    ()
 *  }
 *
 *  // showRaw displays internal structure of a given reflection object
 *  // trees and types (type examples are shown below) are case classes
 *  // so they are shown in a form that's almost copy/pasteable
 *  //
 *  // almost copy/pasteable, but not completely - that's because of symbols
 *  // there's no good way to get a roundtrip-surviving representation of symbols
 *  // in general case, therefore only symbol names are shown (e.g. take a look at AnyRef)
 *  //
 *  // in such a representation, it's impossible to distinguish Idents/Selects
 *  // that have underlying symbols vs ones that don't have symbols, because in both cases
 *  // only names will be printed
 *  //
 *  // to overcome this limitation, use `printIds` and `printKinds` - optional parameters
 *  // of the `showRaw` method (example is shown on the scala.reflect.api.Printers doc page)
 *  scala> showRaw(tree)
 *  res1: String = Block(List(
 *    ClassDef(Modifiers(FINAL), newTypeName("C"), List(), Template(
 *      List(Ident(newTypeName("AnyRef"))),
 *      emptyValDef,
 *      List(
 *        DefDef(Modifiers(), nme.CONSTRUCTOR, List(), List(List()), TypeTree(),
 *          Block(List(
 *            Apply(Select(Super(This(tpnme.EMPTY), tpnme.EMPTY), nme.CONSTRUCTOR), List())),
 *            Literal(Constant(())))),
 *        DefDef(Modifiers(), newTermName("x"), List(), List(), TypeTree(),
 *          Literal(Constant(2))))))),
 *    Literal(Constant(())))
 *  }}}
 */
trait Trees { self: Universe =>

  /** The type of Scala abstract syntax trees. */
  type Tree >: Null <: TreeApi

  /** A tag that preserves the identity of the `Tree` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val TreeTag: ClassTag[Tree]

  /** The API that all trees support.
   *  The main source of information about trees is the [[scala.reflect.api.Trees]] page.
   */
  trait TreeApi extends Product { this: Tree =>
    /** Does this tree represent a definition? (of a method, of a class, etc) */
    def isDef: Boolean

    /** Is this tree one of the empty trees?
     *  Empty trees are: the `EmptyTree` null object, `TypeTree` instances that don't carry a type
     *  and the special `emptyValDef` singleton.
     */
    def isEmpty: Boolean

    /** The canonical way to test if a Tree represents a term.
     */
    def isTerm: Boolean

    /** The canonical way to test if a Tree represents a type.
     */
    def isType: Boolean

    /** Position of the tree. */
    def pos: Position

    /** Type of the tree.
     *
     *  Upon creation most trees have their `tpe` set to `null`.
     *  Types are typically assigned to trees during typechecking.
     */
    def tpe: Type

    /** Symbol of the tree.
     *
     *  For most trees symbol is `null`. In `SymTree`s,
     *  it is overridden and implemented with a var, initialized to `NoSymbol`.
     *
     *  Trees which are not `SymTree`s but which carry symbols do so by
     *  overriding `def symbol` to forward it elsewhere.  Examples:
     *
     *    - `Super(qual, _)`              has `qual`'s symbol,
     *    - `Apply(fun, args)`            has `fun`'s symbol,
     *    - `TypeApply(fun, args)`        has `fun`'s symbol,
     *    - `AppliedTypeTree(tpt, args)`  has `tpt`'s symbol,
     *    - `TypeTree(tpe)`               has `tpe`'s `typeSymbol`, if `tpe != null`.
     */
    def symbol: Symbol

    /** Provides an alternate if tree is empty
     *  @param  alt  The alternate tree
     *  @return If this tree is non empty, this tree, otherwise `alt`.
     */
    def orElse(alt: => Tree): Tree

    /** Apply `f` to each subtree */
    def foreach(f: Tree => Unit): Unit

    /** Find all subtrees matching predicate `p`. Same as `filter` */
    def withFilter(f: Tree => Boolean): List[Tree]

    /** Find all subtrees matching predicate `p`. Same as `withFilter` */
    def filter(f: Tree => Boolean): List[Tree]

    /** Apply `pf' to each subtree on which the function is defined and collect the results.
     */
    def collect[T](pf: PartialFunction[Tree, T]): List[T]

    /** Returns optionally first tree (in a preorder traversal) which satisfies predicate `p`,
     *  or None if none exists.
     */
    def find(p: Tree => Boolean): Option[Tree]

    /** Is there exists a part of this tree which satisfies predicate `p`? */
    def exists(p: Tree => Boolean): Boolean

    /** Do all parts of this tree satisfy predicate `p`? */
    def forAll(p: Tree => Boolean): Boolean

    /** Tests whether two trees are structurall equal.
     *  Note that `==` on trees is reference equality.
     */
    def equalsStructure(that : Tree): Boolean

    /** The direct child trees of this tree.
     *  EmptyTrees are always omitted.  Lists are flattened.
     */
    def children: List[Tree]

    /** Extracts free term symbols from a tree that is reified or contains reified subtrees.
     */
    def freeTerms: List[FreeTermSymbol]

    /** Extracts free type symbols from a tree that is reified or contains reified subtrees.
     */
    def freeTypes: List[FreeTypeSymbol]

    /** Substitute symbols in `to` for corresponding occurrences of references to
     *  symbols `from` in this type.
     */
    def substituteSymbols(from: List[Symbol], to: List[Symbol]): Tree

    /** Substitute types in `to` for corresponding occurrences of references to
     *  symbols `from` in this tree.
     */
    def substituteTypes(from: List[Symbol], to: List[Type]): Tree

    /** Substitute given tree `to` for occurrences of nodes that represent
     *  `C.this`, where `C` referes to the given class `clazz`.
     */
    def substituteThis(clazz: Symbol, to: Tree): Tree

    /** Make a copy of this tree, keeping all attributes,
     *  except that all positions are focused (so nothing
     *  in this tree will be found when searching by position).
     */
    def duplicate: this.type

    /** Obtains string representation of a tree */
    override def toString: String = treeToString(this)
  }

  /** Obtains string representation of a tree */
  protected def treeToString(tree: Tree): String

  /** The empty tree */
  val EmptyTree: Tree

  /** A tree for a term.  Not all trees representing terms are TermTrees; use isTerm
   *  to reliably identify terms.
   */
  type TermTree >: Null <: AnyRef with Tree with TermTreeApi

  /** A tag that preserves the identity of the `TermTree` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val TermTreeTag: ClassTag[TermTree]

  /** The API that all term trees support */
  trait TermTreeApi extends TreeApi { this: TermTree =>
  }

  /** A tree for a type. Not all trees representing types are TypTrees; use isType
   *  to reliably identify types.
   */
  type TypTree >: Null <: AnyRef with Tree with TypTreeApi

  /** A tag that preserves the identity of the `TypTree` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val TypTreeTag: ClassTag[TypTree]

  /** The API that all typ trees support */
  trait TypTreeApi extends TreeApi { this: TypTree =>
  }

  /** A tree with a mutable symbol field, initialized to NoSymbol.
   */
  type SymTree >: Null <: AnyRef with Tree with SymTreeApi

  /** A tag that preserves the identity of the `SymTree` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val SymTreeTag: ClassTag[SymTree]

  /** The API that all sym trees support */
  trait SymTreeApi extends TreeApi { this: SymTree =>
    /** @inheritdoc */
    def symbol: Symbol
  }

  /** A tree with a name - effectively, a DefTree or RefTree.
   */
  type NameTree >: Null <: AnyRef with Tree with NameTreeApi

  /** A tag that preserves the identity of the `NameTree` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val NameTreeTag: ClassTag[NameTree]

  /** The API that all name trees support */
  trait NameTreeApi extends TreeApi { this: NameTree =>
    /** The underlying name.
     *  For example, the `<List>` part of `Ident("List": TermName)`.
     */
    def name: Name
  }

  /** A tree which references a symbol-carrying entity.
   *  References one, as opposed to defining one; definitions
   *  are in DefTrees.
   */
  type RefTree >: Null <: SymTree with NameTree with RefTreeApi

  /** A tag that preserves the identity of the `RefTree` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val RefTreeTag: ClassTag[RefTree]

  /** The API that all ref trees support */
  trait RefTreeApi extends SymTreeApi with NameTreeApi { this: RefTree =>
    /** The qualifier of the reference.
     *  For example, the `<scala>` part of `Select("scala": TermName, "List": TermName)`.
     *  `EmptyTree` for `Ident` instances.
     */
    def qualifier: Tree

    /** @inheritdoc */
    def name: Name
  }

  /** A tree which defines a symbol-carrying entity.
   */
  type DefTree >: Null <: SymTree with NameTree with DefTreeApi

  /** A tag that preserves the identity of the `DefTree` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val DefTreeTag: ClassTag[DefTree]

  /** The API that all def trees support */
  trait DefTreeApi extends SymTreeApi with NameTreeApi { this: DefTree =>
    /** @inheritdoc */
    def name: Name
  }

  /** Common base class for all member definitions: types, classes,
   *  objects, packages, vals and vars, defs.
   */
  type MemberDef >: Null <: DefTree with MemberDefApi

  /** A tag that preserves the identity of the `MemberDef` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val MemberDefTag: ClassTag[MemberDef]

  /** The API that all member defs support */
  trait MemberDefApi extends DefTreeApi { this: MemberDef =>
    /** Modifiers of the declared member. */
    def mods: Modifiers
  }

  /** A packaging, such as `package pid { stats }`
   */
  type PackageDef >: Null <: MemberDef with PackageDefApi

  /** A tag that preserves the identity of the `PackageDef` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val PackageDefTag: ClassTag[PackageDef]

  /** The constructor/deconstructor for `PackageDef` instances. */
  val PackageDef: PackageDefExtractor

  /** An extractor class to create and pattern match with syntax `PackageDef(pid, stats)`.
   *  This AST node corresponds to the following Scala code:
   *
   *    `package` pid { stats }
   */
  abstract class PackageDefExtractor {
    def apply(pid: RefTree, stats: List[Tree]): PackageDef
    def unapply(packageDef: PackageDef): Option[(RefTree, List[Tree])]
  }

  /** The API that all package defs support */
  trait PackageDefApi extends MemberDefApi { this: PackageDef =>
    /** The (possibly, fully-qualified) name of the package. */
    val pid: RefTree

    /** Body of the package definition. */
    val stats: List[Tree]
  }

  /** A common base class for class and object definitions.
   */
  type ImplDef >: Null <: MemberDef with ImplDefApi

  /** A tag that preserves the identity of the `ImplDef` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val ImplDefTag: ClassTag[ImplDef]

  /** The API that all impl defs support */
  trait ImplDefApi extends MemberDefApi { this: ImplDef =>
    /** The body of the definition. */
    val impl: Template
  }

  /** A class definition.
   */
  type ClassDef >: Null <: ImplDef with ClassDefApi

  /** A tag that preserves the identity of the `ClassDef` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val ClassDefTag: ClassTag[ClassDef]

  /** The constructor/deconstructor for `ClassDef` instances. */
  val ClassDef: ClassDefExtractor

  /** An extractor class to create and pattern match with syntax `ClassDef(mods, name, tparams, impl)`.
   *  This AST node corresponds to the following Scala code:
   *
   *    mods `class` name [tparams] impl
   *
   *  Where impl stands for:
   *
   *    `extends` parents { defs }
   */
  abstract class ClassDefExtractor {
    def apply(mods: Modifiers, name: TypeName, tparams: List[TypeDef], impl: Template): ClassDef
    def unapply(classDef: ClassDef): Option[(Modifiers, TypeName, List[TypeDef], Template)]
  }

  /** The API that all class defs support */
  trait ClassDefApi extends ImplDefApi { this: ClassDef =>
    /** @inheritdoc */
    val mods: Modifiers

    /** The name of the class. */
    val name: TypeName

    /** The type parameters of the class. */
    val tparams: List[TypeDef]

    /** @inheritdoc */
    val impl: Template
  }

  /** An object definition, e.g. `object Foo`.  Internally, objects are
   *  quite frequently called modules to reduce ambiguity.
   *  Eliminated by compiler phase refcheck.
   */
  type ModuleDef >: Null <: ImplDef with ModuleDefApi

  /** A tag that preserves the identity of the `ModuleDef` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val ModuleDefTag: ClassTag[ModuleDef]

  /** The constructor/deconstructor for `ModuleDef` instances. */
  val ModuleDef: ModuleDefExtractor

  /** An extractor class to create and pattern match with syntax `ModuleDef(mods, name, impl)`.
   *  This AST node corresponds to the following Scala code:
   *
   *    mods `object` name impl
   *
   *  Where impl stands for:
   *
   *    `extends` parents { defs }
   */
  abstract class ModuleDefExtractor {
    def apply(mods: Modifiers, name: TermName, impl: Template): ModuleDef
    def unapply(moduleDef: ModuleDef): Option[(Modifiers, TermName, Template)]
  }

  /** The API that all module defs support */
  trait ModuleDefApi extends ImplDefApi { this: ModuleDef =>
    /** @inheritdoc */
    val mods: Modifiers

    /** The name of the module. */
    val name: TermName

    /** @inheritdoc */
    val impl: Template
  }

  /** A common base class for ValDefs and DefDefs.
   */
  type ValOrDefDef >: Null <: MemberDef with ValOrDefDefApi

  /** A tag that preserves the identity of the `ValOrDefDef` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val ValOrDefDefTag: ClassTag[ValOrDefDef]

  /** The API that all val defs and def defs support */
  trait ValOrDefDefApi extends MemberDefApi { this: ValOrDefDef =>
    /** @inheritdoc */
    def name: Name // can't be a TermName because macros can be type names.

    /** The type ascribed to the definition.
     *  An empty `TypeTree` if the type hasn't been specified explicitly
     *  and is supposed to be inferred.
     */
    def tpt: Tree

    /** The body of the definition.
     *  The `EmptyTree` is the body is empty (e.g. for abstract members).
     */
    def rhs: Tree
  }

  /** Broadly speaking, a value definition.  All these are encoded as ValDefs:
   *
   *   - immutable values, e.g. "val x"
   *   - mutable values, e.g. "var x" - the MUTABLE flag set in mods
   *   - lazy values, e.g. "lazy val x" - the LAZY flag set in mods
   *   - method parameters, see vparamss in [[scala.reflect.api.Trees#DefDef]] - the PARAM flag is set in mods
   *   - explicit self-types, e.g. class A { self: Bar => }
   */
  type ValDef >: Null <: ValOrDefDef with ValDefApi

  /** A tag that preserves the identity of the `ValDef` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val ValDefTag: ClassTag[ValDef]

  /** The constructor/deconstructor for `ValDef` instances. */
  val ValDef: ValDefExtractor

  /** An extractor class to create and pattern match with syntax `ValDef(mods, name, tpt, rhs)`.
   *  This AST node corresponds to any of the following Scala code:
   *
   *    mods `val` name: tpt = rhs
   *
   *    mods `var` name: tpt = rhs
   *
   *    mods name: tpt = rhs        // in signatures of function and method definitions
   *
   *    self: Bar =>                // self-types
   *
   *  If the type of a value is not specified explicitly (i.e. is meant to be inferred),
   *  this is expressed by having `tpt` set to `TypeTree()` (but not to an `EmptyTree`!).
   */
  abstract class ValDefExtractor {
    def apply(mods: Modifiers, name: TermName, tpt: Tree, rhs: Tree): ValDef
    def unapply(valDef: ValDef): Option[(Modifiers, TermName, Tree, Tree)]
  }

  /** The API that all val defs support */
  trait ValDefApi extends ValOrDefDefApi { this: ValDef =>
    /** @inheritdoc */
    val mods: Modifiers

    /** @inheritdoc */
    val name: TermName

    /** @inheritdoc */
    val tpt: Tree

    /** @inheritdoc */
    val rhs: Tree
  }

  /** A method or macro definition.
   *  @param name   The name of the method or macro. Can be a type name in case this is a type macro
   */
  type DefDef >: Null <: ValOrDefDef with DefDefApi

  /** A tag that preserves the identity of the `DefDef` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val DefDefTag: ClassTag[DefDef]

  /** The constructor/deconstructor for `DefDef` instances. */
  val DefDef: DefDefExtractor

  /** An extractor class to create and pattern match with syntax `DefDef(mods, name, tparams, vparamss, tpt, rhs)`.
   *  This AST node corresponds to the following Scala code:
   *
   *    mods `def` name[tparams](vparams_1)...(vparams_n): tpt = rhs
   *
   *  If the return type is not specified explicitly (i.e. is meant to be inferred),
   *  this is expressed by having `tpt` set to `TypeTree()` (but not to an `EmptyTree`!).
   */
  abstract class DefDefExtractor {
    def apply(mods: Modifiers, name: Name, tparams: List[TypeDef], vparamss: List[List[ValDef]], tpt: Tree, rhs: Tree): DefDef
    def unapply(defDef: DefDef): Option[(Modifiers, Name, List[TypeDef], List[List[ValDef]], Tree, Tree)]
  }

  /** The API that all def defs support */
  trait DefDefApi extends ValOrDefDefApi { this: DefDef =>
    /** @inheritdoc */
    val mods: Modifiers

    /** @inheritdoc */
    val name: Name

    /** The type parameters of the method. */
    val tparams: List[TypeDef]

    /** The parameter lists of the method. */
    val vparamss: List[List[ValDef]]

    /** @inheritdoc */
    val tpt: Tree

    /** @inheritdoc */
    val rhs: Tree
  }

  /** An abstract type, a type parameter, or a type alias.
   *  Eliminated by erasure.
   */
  type TypeDef >: Null <: MemberDef with TypeDefApi

  /** A tag that preserves the identity of the `TypeDef` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val TypeDefTag: ClassTag[TypeDef]

  /** The constructor/deconstructor for `TypeDef` instances. */
  val TypeDef: TypeDefExtractor

  /** An extractor class to create and pattern match with syntax `TypeDef(mods, name, tparams, rhs)`.
   *  This AST node corresponds to the following Scala code:
   *
   *    mods `type` name[tparams] = rhs
   *
   *    mods `type` name[tparams] >: lo <: hi
   *
   *  First usage illustrates `TypeDefs` representing type aliases and type parameters.
   *  Second usage illustrates `TypeDefs` representing abstract types,
   *  where lo and hi are both `TypeBoundsTrees` and `Modifier.deferred` is set in mods.
   */
  abstract class TypeDefExtractor {
    def apply(mods: Modifiers, name: TypeName, tparams: List[TypeDef], rhs: Tree): TypeDef
    def unapply(typeDef: TypeDef): Option[(Modifiers, TypeName, List[TypeDef], Tree)]
  }

  /** The API that all type defs support */
  trait TypeDefApi extends MemberDefApi { this: TypeDef =>
    /** @inheritdoc */
    val mods: Modifiers

    /** @inheritdoc */
    val name: TypeName

    /** The type parameters of this type definition. */
    val tparams: List[TypeDef]

    /** The body of the definition.
     *  The `EmptyTree` is the body is empty (e.g. for abstract type members).
     */
    val rhs: Tree
  }

  /** A labelled expression.  Not expressible in language syntax, but
   *  generated by the compiler to simulate while/do-while loops, and
   *  also by the pattern matcher.
   *
   *  The label acts much like a nested function, where `params` represents
   *  the incoming parameters.  The symbol given to the LabelDef should have
   *  a MethodType, as if it were a nested function.
   *
   *  Jumps are apply nodes attributed with a label's symbol.  The
   *  arguments from the apply node will be passed to the label and
   *  assigned to the Idents.
   *
   *  Forward jumps within a block are allowed.
   */
  type LabelDef >: Null <: DefTree with TermTree with LabelDefApi

  /** A tag that preserves the identity of the `LabelDef` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val LabelDefTag: ClassTag[LabelDef]

  /** The constructor/deconstructor for `LabelDef` instances. */
  val LabelDef: LabelDefExtractor

  /** An extractor class to create and pattern match with syntax `LabelDef(name, params, rhs)`.
   *
   *  This AST node does not have direct correspondence to Scala code.
   *  It is used for tailcalls and like.
   *  For example, while/do are desugared to label defs as follows:
   *  {{{
   *    while (cond) body ==> LabelDef($L, List(), if (cond) { body; L$() } else ())
   *  }}}
   *  {{{
   *    do body while (cond) ==> LabelDef($L, List(), body; if (cond) L$() else ())
   *  }}}
   */
  abstract class LabelDefExtractor {
    def apply(name: TermName, params: List[Ident], rhs: Tree): LabelDef
    def unapply(labelDef: LabelDef): Option[(TermName, List[Ident], Tree)]
  }

  /** The API that all label defs support */
  trait LabelDefApi extends DefTreeApi with TermTreeApi { this: LabelDef =>
    /** @inheritdoc */
    val name: TermName

    /** Label's parameters - names that can be used in the body of the label.
     *  See the example for [[scala.reflect.api.Trees#LabelDefExtractor]].
     */
    val params: List[Ident]

    /** The body of the label.
     *  See the example for [[scala.reflect.api.Trees#LabelDefExtractor]].
     */
    val rhs: Tree
  }

  /** Import selector
   *
   *  Representation of an imported name its optional rename and their optional positions
   *
   *  Eliminated by typecheck.
   *
   * @param name      the imported name
   * @param namePos   its position or -1 if undefined
   * @param rename    the name the import is renamed to (== name if no renaming)
   * @param renamePos the position of the rename or -1 if undefined
   */
  type ImportSelector >: Null <: AnyRef with ImportSelectorApi

  /** A tag that preserves the identity of the `ImportSelector` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val ImportSelectorTag: ClassTag[ImportSelector]

  /** The constructor/deconstructor for `ImportSelector` instances. */
  val ImportSelector: ImportSelectorExtractor

  /** An extractor class to create and pattern match with syntax `ImportSelector(name:, namePos, rename, renamePos)`.
   *  This is not an AST node, it is used as a part of the `Import` node.
   */
  abstract class ImportSelectorExtractor {
    def apply(name: Name, namePos: Int, rename: Name, renamePos: Int): ImportSelector
    def unapply(importSelector: ImportSelector): Option[(Name, Int, Name, Int)]
  }

  /** The API that all import selectors support */
  trait ImportSelectorApi { this: ImportSelector =>
    /** The imported name. */
    val name: Name

    /** Offset of the position of the importing part of the selector in the source file.
     *  Is equal to -1 is the position is unknown.
     */
    val namePos: Int

    /** The name the import is renamed to.
     *  Is equal to `name` if it's not a renaming import.
     */
    val rename: Name

    /** Offset of the position of the renaming part of the selector in the source file.
     *  Is equal to -1 is the position is unknown.
     */
    val renamePos: Int
  }

  /** Import clause
   *
   *  @param expr
   *  @param selectors
   */
  type Import >: Null <: SymTree with ImportApi

  /** A tag that preserves the identity of the `Import` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val ImportTag: ClassTag[Import]

  /** The constructor/deconstructor for `Import` instances. */
  val Import: ImportExtractor

  /** An extractor class to create and pattern match with syntax `Import(expr, selectors)`.
   *  This AST node corresponds to the following Scala code:
   *
   *    import expr.{selectors}
   *
   *  Selectors are a list of ImportSelectors, which conceptually are pairs of names (from, to).
   *  The last (and maybe only name) may be a nme.WILDCARD. For instance:
   *
   *    import qual.{x, y => z, _}
   *
   *  Would be represented as:
   *
   *    Import(qual, List(("x", "x"), ("y", "z"), (WILDCARD, null)))
   *
   *  The symbol of an `Import` is an import symbol @see Symbol.newImport.
   *  It's used primarily as a marker to check that the import has been typechecked.
   */
  abstract class ImportExtractor {
    def apply(expr: Tree, selectors: List[ImportSelector]): Import
    def unapply(import_ : Import): Option[(Tree, List[ImportSelector])]
  }

  /** The API that all imports support */
  trait ImportApi extends SymTreeApi { this: Import =>
    /** The qualifier of the import.
     *  See the example for [[scala.reflect.api.Trees#ImportExtractor]].
     */
    val expr: Tree

    /** The selectors of the import.
     *  See the example for [[scala.reflect.api.Trees#ImportExtractor]].
     */
    val selectors: List[ImportSelector]
  }

  /** Instantiation template of a class or trait
   *
   *  @param parents
   *  @param body
   */
  type Template >: Null <: SymTree with TemplateApi

  /** A tag that preserves the identity of the `Template` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val TemplateTag: ClassTag[Template]

  /** The constructor/deconstructor for `Template` instances. */
  val Template: TemplateExtractor

  /** An extractor class to create and pattern match with syntax `Template(parents, self, body)`.
   *  This AST node corresponds to the following Scala code:
   *
   *    `extends` parents { self => body }
   *
   *  In case when the self-type annotation is missing, it is represented as
   *  an empty value definition with nme.WILDCARD as name and NoType as type.
   *
   *  The symbol of a template is a local dummy. @see Symbol.newLocalDummy
   *  The owner of the local dummy is the enclosing trait or class.
   *  The local dummy is itself the owner of any local blocks. For example:
   *
   *    class C {
   *      def foo { // owner is C
   *        def bar  // owner is local dummy
   *      }
   *    }
   */
  abstract class TemplateExtractor {
    def apply(parents: List[Tree], self: ValDef, body: List[Tree]): Template
    def unapply(template: Template): Option[(List[Tree], ValDef, List[Tree])]
  }

  /** The API that all templates support */
  trait TemplateApi extends SymTreeApi { this: Template =>
    /** Superclasses of the template. */
    val parents: List[Tree]

    /** Self type of the template.
     *  Is equal to `emptyValDef` if the self type is not specified.
     */
    val self: ValDef

    /** Body of the template.
     */
    val body: List[Tree]
  }

  /** Block of expressions (semicolon separated expressions) */
  type Block >: Null <: TermTree with BlockApi

  /** A tag that preserves the identity of the `Block` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val BlockTag: ClassTag[Block]

  /** The constructor/deconstructor for `Block` instances. */
  val Block: BlockExtractor

  /** An extractor class to create and pattern match with syntax `Block(stats, expr)`.
   *  This AST node corresponds to the following Scala code:
   *
   *    { stats; expr }
   *
   *  If the block is empty, the `expr` is set to `Literal(Constant(()))`.
   */
  abstract class BlockExtractor {
    def apply(stats: List[Tree], expr: Tree): Block
    def unapply(block: Block): Option[(List[Tree], Tree)]
  }

  /** The API that all blocks support */
  trait BlockApi extends TermTreeApi { this: Block =>
    /** All, but the last, expressions in the block.
     *  Can very well be an empty list.
     */
    val stats: List[Tree]

    /** The last expression in the block. */
    val expr: Tree
  }

  /** Case clause in a pattern match.
   *  (except for occurrences in switch statements).
   *  Eliminated by compiler phases patmat (in the new pattern matcher of 2.10) or explicitouter (in the old pre-2.10 pattern matcher)
   */
  type CaseDef >: Null <: AnyRef with Tree with CaseDefApi

  /** A tag that preserves the identity of the `CaseDef` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val CaseDefTag: ClassTag[CaseDef]

  /** The constructor/deconstructor for `CaseDef` instances. */
  val CaseDef: CaseDefExtractor

  /** An extractor class to create and pattern match with syntax `CaseDef(pat, guard, body)`.
   *  This AST node corresponds to the following Scala code:
   *
   *    `case` pat `if` guard => body
   *
   *  If the guard is not present, the `guard` is set to `EmptyTree`.
   *  If the body is not specified, the `body` is set to `Literal(Constant())`
   */
  abstract class CaseDefExtractor {
    def apply(pat: Tree, guard: Tree, body: Tree): CaseDef
    def unapply(caseDef: CaseDef): Option[(Tree, Tree, Tree)]
  }

  /** The API that all case defs support */
  trait CaseDefApi extends TreeApi { this: CaseDef =>
    /** The pattern of the pattern matching clause. */
    val pat: Tree

    /** The guard of the pattern matching clause.
     *  Is equal to `EmptyTree` if the guard is not specified.
     */
    val guard: Tree

    /** The body of the pattern matching clause.
     *  Is equal to `Literal(Constant())` if the body is not specified.
     */
    val body: Tree
  }

  /** Alternatives of patterns.
   *
   * Eliminated by compiler phases Eliminated by compiler phases patmat (in the new pattern matcher of 2.10) or explicitouter (in the old pre-2.10 pattern matcher),
   * except for
   *  occurrences in encoded Switch stmt (i.e. remaining Match(CaseDef(...)))
   */
  type Alternative >: Null <: TermTree with AlternativeApi

  /** A tag that preserves the identity of the `Alternative` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val AlternativeTag: ClassTag[Alternative]

  /** The constructor/deconstructor for `Alternative` instances. */
  val Alternative: AlternativeExtractor

  /** An extractor class to create and pattern match with syntax `Alternative(trees)`.
   *  This AST node corresponds to the following Scala code:
   *
   *    pat1 | ... | patn
   */
  abstract class AlternativeExtractor {
    def apply(trees: List[Tree]): Alternative
    def unapply(alternative: Alternative): Option[List[Tree]]
  }

  /** The API that all alternatives support */
  trait AlternativeApi extends TermTreeApi { this: Alternative =>
    /** Alternatives of the pattern matching clause. */
    val trees: List[Tree]
  }

  /** Repetition of pattern.
   *
   *  Eliminated by compiler phases patmat (in the new pattern matcher of 2.10) or explicitouter (in the old pre-2.10 pattern matcher).
   */
  type Star >: Null <: TermTree with StarApi

  /** A tag that preserves the identity of the `Star` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val StarTag: ClassTag[Star]

  /** The constructor/deconstructor for `Star` instances. */
  val Star: StarExtractor

  /** An extractor class to create and pattern match with syntax `Star(elem)`.
   *  This AST node corresponds to the following Scala code:
   *
   *    pat*
   */
  abstract class StarExtractor {
    def apply(elem: Tree): Star
    def unapply(star: Star): Option[Tree]
  }

  /** The API that all stars support */
  trait StarApi extends TermTreeApi { this: Star =>
    /** The quantified pattern. */
    val elem: Tree
  }

  /** Bind a variable to a rhs pattern.
   *
   * Eliminated by compiler phases patmat (in the new pattern matcher of 2.10) or explicitouter (in the old pre-2.10 pattern matcher).
   *
   *  @param name
   *  @param body
   */
  type Bind >: Null <: DefTree with BindApi

  /** A tag that preserves the identity of the `Bind` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val BindTag: ClassTag[Bind]

  /** The constructor/deconstructor for `Bind` instances. */
  val Bind: BindExtractor

  /** An extractor class to create and pattern match with syntax `Bind(name, body)`.
   *  This AST node corresponds to the following Scala code:
   *
   *    pat*
   */
  abstract class BindExtractor {
    def apply(name: Name, body: Tree): Bind
    def unapply(bind: Bind): Option[(Name, Tree)]
  }

  /** The API that all binds support */
  trait BindApi extends DefTreeApi { this: Bind =>
    /** The name that can be used to refer to this fragment of the matched expression.
     *  The `list` part of the `list @ List(x, y)`.
     */
    val name: Name

    /** The pattern that represents this fragment of the matched expression.
     *  The `List(x, y)` part of the `list @ List(x, y)`.
     *  Is equal to `EmptyTree` if the pattern is not specified as in `case x => x`.
     */
    val body: Tree
  }

  /**
   * Used to represent `unapply` methods in pattern matching.
   *
   *  For example:
   *  {{{
   *    2 match { case Foo(x) => x }
   *  }}}
   *
   *  Is represented as:
   *  {{{
   *    Match(
   *      Literal(Constant(2)),
   *      List(
   *        CaseDef(
   *          UnApply(
   *            // a dummy node that carries the type of unapplication to patmat
   *            // the <unapply-selector> here doesn't have an underlying symbol
   *            // it only has a type assigned, therefore after `resetAllAttrs` this tree is no longer typeable
   *            Apply(Select(Ident(Foo), newTermName("unapply")), List(Ident(newTermName("<unapply-selector>")))),
   *            // arguments of the unapply => nothing synthetic here
   *            List(Bind(newTermName("x"), Ident(nme.WILDCARD)))),
   *          EmptyTree,
   *          Ident(newTermName("x")))))
   *  }}}
   *
   * Introduced by typer. Eliminated by compiler phases patmat (in the new pattern matcher of 2.10) or explicitouter (in the old pre-2.10 pattern matcher).
   */
  type UnApply >: Null <: TermTree with UnApplyApi

  /** A tag that preserves the identity of the `UnApply` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val UnApplyTag: ClassTag[UnApply]

  /** The constructor/deconstructor for `UnApply` instances. */
  val UnApply: UnApplyExtractor

  /** An extractor class to create and pattern match with syntax `UnApply(fun, args)`.
   *  This AST node does not have direct correspondence to Scala code,
   *  and is introduced when typechecking pattern matches and `try` blocks.
   */
  abstract class UnApplyExtractor {
    def apply(fun: Tree, args: List[Tree]): UnApply
    def unapply(unApply: UnApply): Option[(Tree, List[Tree])]
  }

  /** The API that all unapplies support */
  trait UnApplyApi extends TermTreeApi { this: UnApply =>
    /** A dummy node that carries the type of unapplication.
     *  See the example for [[scala.reflect.api.Trees#UnApplyExtractor]].
     */
    val fun: Tree

    /** The arguments of the unapplication.
     *  See the example for [[scala.reflect.api.Trees#UnApplyExtractor]].
     */
    val args: List[Tree]
  }

  /** Anonymous function, eliminated by compiler phase lambdalift */
  type Function >: Null <: TermTree with SymTree with FunctionApi

  /** A tag that preserves the identity of the `Function` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val FunctionTag: ClassTag[Function]

  /** The constructor/deconstructor for `Function` instances. */
  val Function: FunctionExtractor

  /** An extractor class to create and pattern match with syntax `Function(vparams, body)`.
   *  This AST node corresponds to the following Scala code:
   *
   *    vparams => body
   *
   *  The symbol of a Function is a synthetic TermSymbol.
   *  It is the owner of the function's parameters.
   */
  abstract class FunctionExtractor {
    def apply(vparams: List[ValDef], body: Tree): Function
    def unapply(function: Function): Option[(List[ValDef], Tree)]
  }

  /** The API that all functions support */
  trait FunctionApi extends TermTreeApi with SymTreeApi { this: Function =>
    /** The list of parameters of the function.
     */
    val vparams: List[ValDef]

    /** The body of the function.
     */
    val body: Tree
  }

  /** Assignment */
  type Assign >: Null <: TermTree with AssignApi

  /** A tag that preserves the identity of the `Assign` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val AssignTag: ClassTag[Assign]

  /** The constructor/deconstructor for `Assign` instances. */
  val Assign: AssignExtractor

  /** An extractor class to create and pattern match with syntax `Assign(lhs, rhs)`.
   *  This AST node corresponds to the following Scala code:
   *
   *    lhs = rhs
   */
  abstract class AssignExtractor {
    def apply(lhs: Tree, rhs: Tree): Assign
    def unapply(assign: Assign): Option[(Tree, Tree)]
  }

  /** The API that all assigns support */
  trait AssignApi extends TermTreeApi { this: Assign =>
    /** The left-hand side of the assignment.
     */
    val lhs: Tree

    /** The right-hand side of the assignment.
     */
    val rhs: Tree
  }

  /** Either an assignment or a named argument. Only appears in argument lists,
   *  eliminated by compiler phase typecheck (doTypedApply), resurrected by reifier.
   */
  type AssignOrNamedArg >: Null <: TermTree with AssignOrNamedArgApi

  /** A tag that preserves the identity of the `AssignOrNamedArg` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val AssignOrNamedArgTag: ClassTag[AssignOrNamedArg]

  /** The constructor/deconstructor for `AssignOrNamedArg` instances. */
  val AssignOrNamedArg: AssignOrNamedArgExtractor

  /** An extractor class to create and pattern match with syntax `AssignOrNamedArg(lhs, rhs)`.
   *  This AST node corresponds to the following Scala code:
   *
   *  {{{
   *    m.f(lhs = rhs)
   *  }}}
   *  {{{
   *    @annotation(lhs = rhs)
   *  }}}
   *
   */
  abstract class AssignOrNamedArgExtractor {
    def apply(lhs: Tree, rhs: Tree): AssignOrNamedArg
    def unapply(assignOrNamedArg: AssignOrNamedArg): Option[(Tree, Tree)]
  }

  /** The API that all assigns support */
  trait AssignOrNamedArgApi extends TermTreeApi { this: AssignOrNamedArg =>
    /** The left-hand side of the expression.
     */
    val lhs: Tree

    /** The right-hand side of the expression.
     */
    val rhs: Tree
  }

  /** Conditional expression */
  type If >: Null <: TermTree with IfApi

  /** A tag that preserves the identity of the `If` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val IfTag: ClassTag[If]

  /** The constructor/deconstructor for `If` instances. */
  val If: IfExtractor

  /** An extractor class to create and pattern match with syntax `If(cond, thenp, elsep)`.
   *  This AST node corresponds to the following Scala code:
   *
   *    `if` (cond) thenp `else` elsep
   *
   *  If the alternative is not present, the `elsep` is set to `Literal(Constant(()))`.
   */
  abstract class IfExtractor {
    def apply(cond: Tree, thenp: Tree, elsep: Tree): If
    def unapply(if_ : If): Option[(Tree, Tree, Tree)]
  }

  /** The API that all ifs support */
  trait IfApi extends TermTreeApi { this: If =>
    /** The condition of the if.
     */
    val cond: Tree

    /** The main branch of the if.
     */
    val thenp: Tree

    /** The alternative of the if.
     *  Is equal to `Literal(Constant(()))` if not specified.
     */
    val elsep: Tree
  }

  /** - Pattern matching expression  (before compiler phase explicitouter before 2.10 / patmat from 2.10)
   *  - Switch statements            (after compiler phase explicitouter before 2.10 / patmat from 2.10)
   *
   *  After compiler phase explicitouter before 2.10 / patmat from 2.10, cases will satisfy the following constraints:
   *
   *  - all guards are `EmptyTree`,
   *  - all patterns will be either `Literal(Constant(x:Int))`
   *    or `Alternative(lit|...|lit)`
   *  - except for an "otherwise" branch, which has pattern
   *    `Ident(nme.WILDCARD)`
   */
  type Match >: Null <: TermTree with MatchApi

  /** A tag that preserves the identity of the `Match` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val MatchTag: ClassTag[Match]

  /** The constructor/deconstructor for `Match` instances. */
  val Match: MatchExtractor

  /** An extractor class to create and pattern match with syntax `Match(selector, cases)`.
   *  This AST node corresponds to the following Scala code:
   *
   *    selector `match` { cases }
   *
   * `Match` is also used in pattern matching assignments like `val (foo, bar) = baz`.
   */
  abstract class MatchExtractor {
    def apply(selector: Tree, cases: List[CaseDef]): Match
    def unapply(match_ : Match): Option[(Tree, List[CaseDef])]
  }

  /** The API that all matches support */
  trait MatchApi extends TermTreeApi { this: Match =>
    /** The scrutinee of the pattern match. */
    val selector: Tree

    /** The arms of the pattern match. */
    val cases: List[CaseDef]
  }

  /** Return expression */
  type Return >: Null <: TermTree with SymTree with ReturnApi

  /** A tag that preserves the identity of the `Return` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val ReturnTag: ClassTag[Return]

  /** The constructor/deconstructor for `Return` instances. */
  val Return: ReturnExtractor

  /** An extractor class to create and pattern match with syntax `Return(expr)`.
   *  This AST node corresponds to the following Scala code:
   *
   *    `return` expr
   *
   *  The symbol of a Return node is the enclosing method.
   */
  abstract class ReturnExtractor {
    def apply(expr: Tree): Return
    def unapply(return_ : Return): Option[Tree]
  }

  /** The API that all returns support */
  trait ReturnApi extends TermTreeApi { this: Return =>
    /** The returned expression. */
    val expr: Tree
  }

  /** TODO comment me! */
  type Try >: Null <: TermTree with TryApi

  /** A tag that preserves the identity of the `Try` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val TryTag: ClassTag[Try]

  /** The constructor/deconstructor for `Try` instances. */
  val Try: TryExtractor

  /** An extractor class to create and pattern match with syntax `Try(block, catches, finalizer)`.
   *  This AST node corresponds to the following Scala code:
   *
   *    `try` block `catch` { catches } `finally` finalizer
   *
   *  If the finalizer is not present, the `finalizer` is set to `EmptyTree`.
   */
  abstract class TryExtractor {
    def apply(block: Tree, catches: List[CaseDef], finalizer: Tree): Try
    def unapply(try_ : Try): Option[(Tree, List[CaseDef], Tree)]
  }

  /** The API that all tries support */
  trait TryApi extends TermTreeApi { this: Try =>
    /** The protected block. */
    val block: Tree

    /** The `catch` pattern-matching clauses of the try. */
    val catches: List[CaseDef]

    /** The `finally` part of the try. */
    val finalizer: Tree
  }

  /** Throw expression */
  type Throw >: Null <: TermTree with ThrowApi

  /** A tag that preserves the identity of the `Throw` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val ThrowTag: ClassTag[Throw]

  /** The constructor/deconstructor for `Throw` instances. */
  val Throw: ThrowExtractor

  /** An extractor class to create and pattern match with syntax `Throw(expr)`.
   *  This AST node corresponds to the following Scala code:
   *
   *    `throw` expr
   */
  abstract class ThrowExtractor {
    def apply(expr: Tree): Throw
    def unapply(throw_ : Throw): Option[Tree]
  }

  /** The API that all tries support */
  trait ThrowApi extends TermTreeApi { this: Throw =>
    /** The thrown expression. */
    val expr: Tree
  }

  /** Object instantiation
   */
  type New >: Null <: TermTree with NewApi

  /** A tag that preserves the identity of the `New` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val NewTag: ClassTag[New]

  /** The constructor/deconstructor for `New` instances.
   */
  val New: NewExtractor

  /** An extractor class to create and pattern match with syntax `New(tpt)`.
   *  This AST node corresponds to the following Scala code:
   *
   *    `new` T
   *
   *  This node always occurs in the following context:
   *
   *    (`new` tpt).<init>[targs](args)
   */
  abstract class NewExtractor {
    /** A user level `new`.
     *  One should always use this factory method to build a user level `new`.
     *
     *  @param tpt    a class type
     */
    def apply(tpt: Tree): New
    def unapply(new_ : New): Option[Tree]
  }

  /** The API that all news support */
  trait NewApi extends TermTreeApi { this: New =>
    /** The tree that represents the type being instantiated.
     *  See the example for [[scala.reflect.api.Trees#NewExtractor]].
     */
    val tpt: Tree
  }

  /** Type annotation, eliminated by compiler phase cleanup */
  type Typed >: Null <: TermTree with TypedApi

  /** A tag that preserves the identity of the `Typed` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val TypedTag: ClassTag[Typed]

  /** The constructor/deconstructor for `Typed` instances. */
  val Typed: TypedExtractor

  /** An extractor class to create and pattern match with syntax `Typed(expr, tpt)`.
   *  This AST node corresponds to the following Scala code:
   *
   *    expr: tpt
   */
  abstract class TypedExtractor {
    def apply(expr: Tree, tpt: Tree): Typed
    def unapply(typed: Typed): Option[(Tree, Tree)]
  }

  /** The API that all typeds support */
  trait TypedApi extends TermTreeApi { this: Typed =>
    /** The expression being ascribed with the type. */
    val expr: Tree

    /** The type being ascribed to the expression. */
    val tpt: Tree
  }

  /** Common base class for Apply and TypeApply.
   */
  type GenericApply >: Null <: TermTree with GenericApplyApi

  /** A tag that preserves the identity of the `GenericApply` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val GenericApplyTag: ClassTag[GenericApply]

  /** The API that all applies support */
  trait GenericApplyApi extends TermTreeApi { this: GenericApply =>
    /** The target of the application. */
    val fun: Tree

    /** The arguments of the application. */
    val args: List[Tree]
  }

  /* @PP: All signs point toward it being a requirement that args.nonEmpty,
   *  but I can't find that explicitly stated anywhere.  Unless your last name
   *  is odersky, you should probably treat it as true.
   */
  /** Explicit type application. */
  type TypeApply >: Null <: GenericApply with TypeApplyApi

  /** A tag that preserves the identity of the `TypeApply` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val TypeApplyTag: ClassTag[TypeApply]

  /** The constructor/deconstructor for `TypeApply` instances. */
  val TypeApply: TypeApplyExtractor

  /** An extractor class to create and pattern match with syntax `TypeApply(fun, args)`.
   *  This AST node corresponds to the following Scala code:
   *
   *    fun[args]
   */
  abstract class TypeApplyExtractor {
    def apply(fun: Tree, args: List[Tree]): TypeApply
    def unapply(typeApply: TypeApply): Option[(Tree, List[Tree])]
  }

  /** The API that all type applies support */
  trait TypeApplyApi extends GenericApplyApi { this: TypeApply =>
  }

  /** Value application */
  type Apply >: Null <: GenericApply with ApplyApi

  /** A tag that preserves the identity of the `Apply` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val ApplyTag: ClassTag[Apply]

  /** The constructor/deconstructor for `Apply` instances. */
  val Apply: ApplyExtractor

  /** An extractor class to create and pattern match with syntax `Apply(fun, args)`.
   *  This AST node corresponds to the following Scala code:
   *
   *    fun(args)
   *
   *  For instance:
   *
   *    fun[targs](args)
   *
   *  Is expressed as:
   *
   *    Apply(TypeApply(fun, targs), args)
   */
  abstract class ApplyExtractor {
    def apply(fun: Tree, args: List[Tree]): Apply
    def unapply(apply: Apply): Option[(Tree, List[Tree])]
  }

  /** The API that all applies support */
  trait ApplyApi extends GenericApplyApi { this: Apply =>
  }

  /** Super reference, where `qual` is the corresponding `this` reference.
   *  A super reference `C.super[M]` is represented as `Super(This(C), M)`.
   */
  type Super >: Null <: TermTree with SuperApi

  /** A tag that preserves the identity of the `Super` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val SuperTag: ClassTag[Super]

  /** The constructor/deconstructor for `Super` instances. */
  val Super: SuperExtractor

  /** An extractor class to create and pattern match with syntax `Super(qual, mix)`.
   *  This AST node corresponds to the following Scala code:
   *
   *    C.super[M]
   *
   *  Which is represented as:
   *
   *    Super(This(C), M)
   *
   *  If `mix` is empty, it is tpnme.EMPTY.
   *
   *  The symbol of a Super is the class _from_ which the super reference is made.
   *  For instance in C.super(...), it would be C.
   */
  abstract class SuperExtractor {
    def apply(qual: Tree, mix: TypeName): Super
    def unapply(super_ : Super): Option[(Tree, TypeName)]
  }

  /** The API that all supers support */
  trait SuperApi extends TermTreeApi { this: Super =>
    /** The qualifier of the `super` expression.
     *  See the example for [[scala.reflect.api.Trees#SuperExtractor]].
     */
    val qual: Tree

    /** The selector of the `super` expression.
     *  See the example for [[scala.reflect.api.Trees#SuperExtractor]].
     */
    val mix: TypeName
  }

  /** Self reference */
  type This >: Null <: TermTree with SymTree with ThisApi

  /** A tag that preserves the identity of the `This` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val ThisTag: ClassTag[This]

  /** The constructor/deconstructor for `This` instances. */
  val This: ThisExtractor

  /** An extractor class to create and pattern match with syntax `This(qual)`.
   *  This AST node corresponds to the following Scala code:
   *
   *    qual.this
   *
   *  The symbol of a This is the class to which the this refers.
   *  For instance in C.this, it would be C.
   */
  abstract class ThisExtractor {
    def apply(qual: TypeName): This
    def unapply(this_ : This): Option[TypeName]
  }

  /** The API that all thises support */
  trait ThisApi extends TermTreeApi with SymTreeApi { this: This =>
    /** The qualifier of the `this` expression.
     *  For an unqualified `this` refers to the enclosing class.
     */
    val qual: TypeName
  }

  /** Designator <qualifier> . <name> */
  type Select >: Null <: RefTree with SelectApi

  /** A tag that preserves the identity of the `Select` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val SelectTag: ClassTag[Select]

  /** The constructor/deconstructor for `Select` instances. */
  val Select: SelectExtractor

  /** An extractor class to create and pattern match with syntax `Select(qual, name)`.
   *  This AST node corresponds to the following Scala code:
   *
   *    qualifier.selector
   */
  abstract class SelectExtractor {
    def apply(qualifier: Tree, name: Name): Select
    def unapply(select: Select): Option[(Tree, Name)]
  }

  /** The API that all selects support */
  trait SelectApi extends RefTreeApi { this: Select =>
    /** @inheritdoc */
    val qualifier: Tree

    /** @inheritdoc */
    val name: Name
  }

  /** Identifier <name> */
  type Ident >: Null <: RefTree with IdentApi

  /** A tag that preserves the identity of the `Ident` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val IdentTag: ClassTag[Ident]

  /** The constructor/deconstructor for `Ident` instances. */
  val Ident: IdentExtractor

  /** An extractor class to create and pattern match with syntax `Ident(qual, name)`.
   *  This AST node corresponds to the following Scala code:
   *
   *    name
   *
   *  Type checker converts idents that refer to enclosing fields or methods to selects.
   *  For example, name ==> this.name
   */
  abstract class IdentExtractor {
    def apply(name: Name): Ident
    def unapply(ident: Ident): Option[Name]
  }

  /** The API that all idents support */
  trait IdentApi extends RefTreeApi { this: Ident =>
    /** @inheritdoc */
    val name: Name
  }

  /** Marks underlying reference to id as boxed.
   *  @pre id must refer to a captured variable
   *  A reference such marked will refer to the boxed entity, no dereferencing
   *  with `.elem` is done on it.
   *  This tree node can be emitted by macros such as reify that call referenceCapturedVariable.
   *  It is eliminated in LambdaLift, where the boxing conversion takes place.
   */
  type ReferenceToBoxed >: Null <: TermTree with ReferenceToBoxedApi

  /** A tag that preserves the identity of the `ReferenceToBoxed` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val ReferenceToBoxedTag: ClassTag[ReferenceToBoxed]

  /** The constructor/deconstructor for `ReferenceToBoxed` instances. */
  val ReferenceToBoxed: ReferenceToBoxedExtractor

  /** An extractor class to create and pattern match with syntax `ReferenceToBoxed(ident)`.
   *  This AST node does not have direct correspondence to Scala code,
   *  and is emitted by macros to reference capture vars directly without going through `elem`.
   *
   *  For example:
   *
   *    var x = ...
   *    fun { x }
   *
   *  Will emit:
   *
   *    Ident(x)
   *
   *  Which gets transformed to:
   *
   *    Select(Ident(x), "elem")
   *
   *  If `ReferenceToBoxed` were used instead of Ident, no transformation would be performed.
   */
  abstract class ReferenceToBoxedExtractor {
    def apply(ident: Ident): ReferenceToBoxed
    def unapply(referenceToBoxed: ReferenceToBoxed): Option[Ident]
  }

  /** The API that all references support */
  trait ReferenceToBoxedApi extends TermTreeApi { this: ReferenceToBoxed =>
    /** The underlying reference. */
    val ident: Tree
  }

  /** Literal */
  type Literal >: Null <: TermTree with LiteralApi

  /** A tag that preserves the identity of the `Literal` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val LiteralTag: ClassTag[Literal]

  /** The constructor/deconstructor for `Literal` instances. */
  val Literal: LiteralExtractor

  /** An extractor class to create and pattern match with syntax `Literal(value)`.
   *  This AST node corresponds to the following Scala code:
   *
   *    value
   */
  abstract class LiteralExtractor {
    def apply(value: Constant): Literal
    def unapply(literal: Literal): Option[Constant]
  }

  /** The API that all literals support */
  trait LiteralApi extends TermTreeApi { this: Literal =>
    /** The compile-time constant underlying the literal. */
    val value: Constant
  }

  /** A tree that has an annotation attached to it. Only used for annotated types and
   *  annotation ascriptions, annotations on definitions are stored in the Modifiers.
   *  Eliminated by typechecker (typedAnnotated), the annotations are then stored in
   *  an AnnotatedType.
   */
  type Annotated >: Null <: AnyRef with Tree with AnnotatedApi

  /** A tag that preserves the identity of the `Annotated` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val AnnotatedTag: ClassTag[Annotated]

  /** The constructor/deconstructor for `Annotated` instances. */
  val Annotated: AnnotatedExtractor

  /** An extractor class to create and pattern match with syntax `Annotated(annot, arg)`.
   *  This AST node corresponds to the following Scala code:
   *
   *    arg @annot    // for types
   *    arg: @annot   // for exprs
   */
  abstract class AnnotatedExtractor {
    def apply(annot: Tree, arg: Tree): Annotated
    def unapply(annotated: Annotated): Option[(Tree, Tree)]
  }

  /** The API that all annotateds support */
  trait AnnotatedApi extends TreeApi { this: Annotated =>
    /** The annotation. */
    val annot: Tree

    /** The annotee. */
    val arg: Tree
  }

  /** Singleton type, eliminated by RefCheck */
  type SingletonTypeTree >: Null <: TypTree with SingletonTypeTreeApi

  /** A tag that preserves the identity of the `SingletonTypeTree` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val SingletonTypeTreeTag: ClassTag[SingletonTypeTree]

  /** The constructor/deconstructor for `SingletonTypeTree` instances. */
  val SingletonTypeTree: SingletonTypeTreeExtractor

  /** An extractor class to create and pattern match with syntax `SingletonTypeTree(ref)`.
   *  This AST node corresponds to the following Scala code:
   *
   *    ref.type
   */
  abstract class SingletonTypeTreeExtractor {
    def apply(ref: Tree): SingletonTypeTree
    def unapply(singletonTypeTree: SingletonTypeTree): Option[Tree]
  }

  /** The API that all singleton type trees support */
  trait SingletonTypeTreeApi extends TypTreeApi { this: SingletonTypeTree =>
    /** The underlying reference. */
    val ref: Tree
  }

  /** Type selection <qualifier> # <name>, eliminated by RefCheck */
  // [Eugene++] don't see why we need it, when we have Select
  type SelectFromTypeTree >: Null <: TypTree with RefTree with SelectFromTypeTreeApi

  /** A tag that preserves the identity of the `SelectFromTypeTree` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val SelectFromTypeTreeTag: ClassTag[SelectFromTypeTree]

  /** The constructor/deconstructor for `SelectFromTypeTree` instances. */
  val SelectFromTypeTree: SelectFromTypeTreeExtractor

  /** An extractor class to create and pattern match with syntax `SelectFromTypeTree(qualifier, name)`.
   *  This AST node corresponds to the following Scala code:
   *
   *    qualifier # selector
   *
   *  Note: a path-dependent type p.T is expressed as p.type # T
   */
  abstract class SelectFromTypeTreeExtractor {
    def apply(qualifier: Tree, name: TypeName): SelectFromTypeTree
    def unapply(selectFromTypeTree: SelectFromTypeTree): Option[(Tree, TypeName)]
  }

  /** The API that all selects from type trees support */
  trait SelectFromTypeTreeApi extends TypTreeApi with RefTreeApi { this: SelectFromTypeTree =>
    /** @inheritdoc */
    val qualifier: Tree

    /** @inheritdoc */
    val name: TypeName
  }

  /** Intersection type <parent1> with ... with <parentN> { <decls> }, eliminated by RefCheck */
  type CompoundTypeTree >: Null <: TypTree with CompoundTypeTreeApi

  /** A tag that preserves the identity of the `CompoundTypeTree` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val CompoundTypeTreeTag: ClassTag[CompoundTypeTree]

  /** The constructor/deconstructor for `CompoundTypeTree` instances. */
  val CompoundTypeTree: CompoundTypeTreeExtractor

  /** An extractor class to create and pattern match with syntax `CompoundTypeTree(templ)`.
   *  This AST node corresponds to the following Scala code:
   *
   *    parent1 with ... with parentN { refinement }
   */
  abstract class CompoundTypeTreeExtractor {
    def apply(templ: Template): CompoundTypeTree
    def unapply(compoundTypeTree: CompoundTypeTree): Option[Template]
  }

  /** The API that all compound type trees support */
  trait CompoundTypeTreeApi extends TypTreeApi { this: CompoundTypeTree =>
    /** The template of the compound type - represents the parents, the optional self-type and the optional definitions. */
    val templ: Template
  }

  /** Applied type <tpt> [ <args> ], eliminated by RefCheck */
  type AppliedTypeTree >: Null <: TypTree with AppliedTypeTreeApi

  /** A tag that preserves the identity of the `AppliedTypeTree` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val AppliedTypeTreeTag: ClassTag[AppliedTypeTree]

  /** The constructor/deconstructor for `AppliedTypeTree` instances. */
  val AppliedTypeTree: AppliedTypeTreeExtractor

  /** An extractor class to create and pattern match with syntax `AppliedTypeTree(tpt, args)`.
   *  This AST node corresponds to the following Scala code:
   *
   *    tpt[args]
   */
  abstract class AppliedTypeTreeExtractor {
    def apply(tpt: Tree, args: List[Tree]): AppliedTypeTree
    def unapply(appliedTypeTree: AppliedTypeTree): Option[(Tree, List[Tree])]
  }

  /** The API that all applied type trees support */
  trait AppliedTypeTreeApi extends TypTreeApi { this: AppliedTypeTree =>
    /** The target of the application. */
    val tpt: Tree

    /** The arguments of the application. */
    val args: List[Tree]
  }

  /** Document me! */
  type TypeBoundsTree >: Null <: TypTree with TypeBoundsTreeApi

  /** A tag that preserves the identity of the `TypeBoundsTree` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val TypeBoundsTreeTag: ClassTag[TypeBoundsTree]

  /** The constructor/deconstructor for `TypeBoundsTree` instances. */
  val TypeBoundsTree: TypeBoundsTreeExtractor

  /** An extractor class to create and pattern match with syntax `TypeBoundsTree(lo, hi)`.
   *  This AST node corresponds to the following Scala code:
   *
   *    >: lo <: hi
   */
  abstract class TypeBoundsTreeExtractor {
    def apply(lo: Tree, hi: Tree): TypeBoundsTree
    def unapply(typeBoundsTree: TypeBoundsTree): Option[(Tree, Tree)]
  }

  /** The API that all type bound trees support */
  trait TypeBoundsTreeApi extends TypTreeApi { this: TypeBoundsTree =>
    /** The lower bound.
     *  Is equal to `Ident(<scala.Nothing>)` if not specified explicitly.
     */
    val lo: Tree

    /** The upper bound.
     *  Is equal to `Ident(<scala.Any>)` if not specified explicitly.
     */
    val hi: Tree
  }

  /** TODO Document me! */
  type ExistentialTypeTree >: Null <: TypTree with ExistentialTypeTreeApi

  /** A tag that preserves the identity of the `ExistentialTypeTree` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val ExistentialTypeTreeTag: ClassTag[ExistentialTypeTree]

  /** The constructor/deconstructor for `ExistentialTypeTree` instances. */
  val ExistentialTypeTree: ExistentialTypeTreeExtractor

  /** An extractor class to create and pattern match with syntax `ExistentialTypeTree(tpt, whereClauses)`.
   *  This AST node corresponds to the following Scala code:
   *
   *    tpt forSome { whereClauses }
   */
  abstract class ExistentialTypeTreeExtractor {
    def apply(tpt: Tree, whereClauses: List[Tree]): ExistentialTypeTree
    def unapply(existentialTypeTree: ExistentialTypeTree): Option[(Tree, List[Tree])]
  }

  /** The API that all existential type trees support */
  trait ExistentialTypeTreeApi extends TypTreeApi { this: ExistentialTypeTree =>
    /** The underlying type of the existential type. */
    val tpt: Tree

    /** The clauses of the definition of the existential type. */
    val whereClauses: List[Tree]
  }

  /** A synthetic tree holding an arbitrary type.  Not to be confused with
    * with TypTree, the trait for trees that are only used for type trees.
    * TypeTree's are inserted in several places, but most notably in
    * `RefCheck`, where the arbitrary type trees are all replaced by
    * TypeTree's. */
  type TypeTree >: Null <: TypTree with TypeTreeApi

  /** A tag that preserves the identity of the `TypeTree` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val TypeTreeTag: ClassTag[TypeTree]

  /** The constructor/deconstructor for `TypeTree` instances. */
  val TypeTree: TypeTreeExtractor

  /** An extractor class to create and pattern match with syntax `TypeTree()`.
   *  This AST node does not have direct correspondence to Scala code,
   *  and is emitted by everywhere when we want to wrap a `Type` in a `Tree`.
   */
  abstract class TypeTreeExtractor {
    def apply(): TypeTree
    def unapply(typeTree: TypeTree): Boolean
  }

  /** The API that all type trees support */
  trait TypeTreeApi extends TypTreeApi { this: TypeTree =>
    /** The precursor of this tree.
     *  Is equal to `EmptyTree` if this type tree doesn't have precursors.
     */
    def original: Tree
  }

  /** An empty deferred value definition corresponding to:
   *    val _: _
   *  This is used as a placeholder in the `self` parameter Template if there is
   *  no definition of a self value of self type.
   */
  val emptyValDef: ValDef

// ---------------------- factories ----------------------------------------------

  /** A factory method for `ClassDef` nodes.
   */
  def ClassDef(sym: Symbol, impl: Template): ClassDef

  /** A factory method for `ModuleDef` nodes.
   */
  def ModuleDef(sym: Symbol, impl: Template): ModuleDef

  /** A factory method for `ValDef` nodes.
   */
  def ValDef(sym: Symbol, rhs: Tree): ValDef

  /** A factory method for `ValDef` nodes.
   */
  def ValDef(sym: Symbol): ValDef

  /** A factory method for `ValDef` nodes.
   */
  def DefDef(sym: Symbol, mods: Modifiers, vparamss: List[List[ValDef]], rhs: Tree): DefDef

  /** A factory method for `ValDef` nodes.
   */
  def DefDef(sym: Symbol, vparamss: List[List[ValDef]], rhs: Tree): DefDef

  /** A factory method for `ValDef` nodes.
   */
  def DefDef(sym: Symbol, mods: Modifiers, rhs: Tree): DefDef

  /** A factory method for `ValDef` nodes.
   */
  def DefDef(sym: Symbol, rhs: Tree): DefDef

  /** A factory method for `ValDef` nodes.
   */
  def DefDef(sym: Symbol, rhs: List[List[Symbol]] => Tree): DefDef

  /** A factory method for `TypeDef` nodes.
   */
  def TypeDef(sym: Symbol, rhs: Tree): TypeDef

  /** A factory method for `TypeDef` nodes.
   */
  def TypeDef(sym: Symbol): TypeDef

  /** A factory method for `LabelDef` nodes.
   */
  def LabelDef(sym: Symbol, params: List[Symbol], rhs: Tree): LabelDef

  /** A factory method for `Block` nodes.
   *  Flattens directly nested blocks.
   */
  def Block(stats: Tree*): Block

  /** A factory method for `CaseDef` nodes.
   */
  def CaseDef(pat: Tree, body: Tree): CaseDef

  /** A factory method for `Bind` nodes.
   */
  def Bind(sym: Symbol, body: Tree): Bind

  /** A factory method for `Try` nodes.
   */
  def Try(body: Tree, cases: (Tree, Tree)*): Try

  /** A factory method for `Throw` nodes.
   */
  def Throw(tpe: Type, args: Tree*): Throw

  /** Factory method for object creation `new tpt(args_1)...(args_n)`
   *  A `New(t, as)` is expanded to: `(new t).<init>(as)`
   */
  def New(tpt: Tree, argss: List[List[Tree]]): Tree

  /** 0-1 argument list new, based on a type.
   */
  def New(tpe: Type, args: Tree*): Tree

  /** 0-1 argument list new, based on a symbol.
   */
  def New(sym: Symbol, args: Tree*): Tree

  /** A factory method for `Apply` nodes.
   */
  def Apply(sym: Symbol, args: Tree*): Tree

  /** 0-1 argument list new, based on a type tree.
   */
  def ApplyConstructor(tpt: Tree, args: List[Tree]): Tree

  /** A factory method for `Super` nodes.
   */
  def Super(sym: Symbol, mix: TypeName): Tree

  /** A factory method for `This` nodes.
   */
  def This(sym: Symbol): Tree

  /** A factory method for `Select` nodes.
   *  The string `name` argument is assumed to represent a [[scala.reflect.api.Names#TermName].
   */
  def Select(qualifier: Tree, name: String): Select

  /** A factory method for `Select` nodes.
   */
  def Select(qualifier: Tree, sym: Symbol): Select

  /** A factory method for `Ident` nodes.
   */
  def Ident(name: String): Ident

  /** A factory method for `Ident` nodes.
   */
  def Ident(sym: Symbol): Ident

  /** A factory method for `TypeTree` nodes.
   */
  def TypeTree(tp: Type): TypeTree

// ---------------------- copying ------------------------------------------------

  /** The type of standard (lazy) tree copiers.
   */
  type TreeCopier <: TreeCopierOps

  /** The standard (lazy) tree copier.
   */
  val treeCopy: TreeCopier = newLazyTreeCopier

  /** Creates a strict tree copier.
   */
  def newStrictTreeCopier: TreeCopier

  /** Creates a lazy tree copier.
   */
  def newLazyTreeCopier: TreeCopier

  /** The API of a tree copier.
   */
  abstract class TreeCopierOps {
    /** Creates a `ClassDef` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def ClassDef(tree: Tree, mods: Modifiers, name: Name, tparams: List[TypeDef], impl: Template): ClassDef

    /** Creates a `PackageDef` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def PackageDef(tree: Tree, pid: RefTree, stats: List[Tree]): PackageDef

    /** Creates a `ModuleDef` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def ModuleDef(tree: Tree, mods: Modifiers, name: Name, impl: Template): ModuleDef

    /** Creates a `ValDef` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def ValDef(tree: Tree, mods: Modifiers, name: Name, tpt: Tree, rhs: Tree): ValDef

    /** Creates a `DefDef` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def DefDef(tree: Tree, mods: Modifiers, name: Name, tparams: List[TypeDef], vparamss: List[List[ValDef]], tpt: Tree, rhs: Tree): DefDef

    /** Creates a `TypeDef` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def TypeDef(tree: Tree, mods: Modifiers, name: Name, tparams: List[TypeDef], rhs: Tree): TypeDef

    /** Creates a `LabelDef` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def LabelDef(tree: Tree, name: Name, params: List[Ident], rhs: Tree): LabelDef

    /** Creates a `Import` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def Import(tree: Tree, expr: Tree, selectors: List[ImportSelector]): Import

    /** Creates a `Template` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def Template(tree: Tree, parents: List[Tree], self: ValDef, body: List[Tree]): Template

    /** Creates a `Block` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def Block(tree: Tree, stats: List[Tree], expr: Tree): Block

    /** Creates a `CaseDef` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def CaseDef(tree: Tree, pat: Tree, guard: Tree, body: Tree): CaseDef

    /** Creates a `Alternative` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def Alternative(tree: Tree, trees: List[Tree]): Alternative

    /** Creates a `Star` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def Star(tree: Tree, elem: Tree): Star

    /** Creates a `Bind` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def Bind(tree: Tree, name: Name, body: Tree): Bind

    /** Creates a `UnApply` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def UnApply(tree: Tree, fun: Tree, args: List[Tree]): UnApply

    /** Creates a `Function` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def Function(tree: Tree, vparams: List[ValDef], body: Tree): Function

    /** Creates a `Assign` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def Assign(tree: Tree, lhs: Tree, rhs: Tree): Assign

    /** Creates a `AssignOrNamedArg` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def AssignOrNamedArg(tree: Tree, lhs: Tree, rhs: Tree): AssignOrNamedArg

    /** Creates a `If` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def If(tree: Tree, cond: Tree, thenp: Tree, elsep: Tree): If

    /** Creates a `Match` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def Match(tree: Tree, selector: Tree, cases: List[CaseDef]): Match

    /** Creates a `Return` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def Return(tree: Tree, expr: Tree): Return

    /** Creates a `Try` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def Try(tree: Tree, block: Tree, catches: List[CaseDef], finalizer: Tree): Try

    /** Creates a `Throw` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def Throw(tree: Tree, expr: Tree): Throw

    /** Creates a `New` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def New(tree: Tree, tpt: Tree): New

    /** Creates a `Typed` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def Typed(tree: Tree, expr: Tree, tpt: Tree): Typed

    /** Creates a `TypeApply` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def TypeApply(tree: Tree, fun: Tree, args: List[Tree]): TypeApply

    /** Creates a `Apply` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def Apply(tree: Tree, fun: Tree, args: List[Tree]): Apply

    /** Creates a `Super` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def Super(tree: Tree, qual: Tree, mix: TypeName): Super

    /** Creates a `This` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def This(tree: Tree, qual: Name): This

    /** Creates a `Select` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def Select(tree: Tree, qualifier: Tree, selector: Name): Select

    /** Creates a `Ident` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def Ident(tree: Tree, name: Name): Ident

    /** Creates a `ReferenceToBoxed` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def ReferenceToBoxed(tree: Tree, idt: Ident): ReferenceToBoxed

    /** Creates a `Literal` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def Literal(tree: Tree, value: Constant): Literal

    /** Creates a `TypeTree` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def TypeTree(tree: Tree): TypeTree

    /** Creates a `Annotated` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def Annotated(tree: Tree, annot: Tree, arg: Tree): Annotated

    /** Creates a `SingletonTypeTree` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def SingletonTypeTree(tree: Tree, ref: Tree): SingletonTypeTree

    /** Creates a `SelectFromTypeTree` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def SelectFromTypeTree(tree: Tree, qualifier: Tree, selector: Name): SelectFromTypeTree

    /** Creates a `CompoundTypeTree` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def CompoundTypeTree(tree: Tree, templ: Template): CompoundTypeTree

    /** Creates a `AppliedTypeTree` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def AppliedTypeTree(tree: Tree, tpt: Tree, args: List[Tree]): AppliedTypeTree

    /** Creates a `TypeBoundsTree` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def TypeBoundsTree(tree: Tree, lo: Tree, hi: Tree): TypeBoundsTree

    /** Creates a `ExistentialTypeTree` node from the given components, having a given `tree` as a prototype.
     *  Having a tree as a prototype means that the tree's attachments, type and symbol will be copied into the result.
     */
    def ExistentialTypeTree(tree: Tree, tpt: Tree, whereClauses: List[Tree]): ExistentialTypeTree
  }

// ---------------------- traversing and transforming ------------------------------

  /** A class that implement a default tree traversal strategy: breadth-first component-wise.
   */
  class Traverser {
    protected[scala] var currentOwner: Symbol = rootMirror.RootClass

    /** Traverses a single tree. */
    def traverse(tree: Tree): Unit = itraverse(this, tree)

    /** Traverses a list of trees. */
    def traverseTrees(trees: List[Tree]) {
      trees foreach traverse
    }

    /** Traverses a list of lists of trees. */
    def traverseTreess(treess: List[List[Tree]]) {
      treess foreach traverseTrees
    }

    /** Traverses a list of trees with a given owner symbol. */
    def traverseStats(stats: List[Tree], exprOwner: Symbol) {
      stats foreach (stat =>
        if (exprOwner != currentOwner) atOwner(exprOwner)(traverse(stat))
        else traverse(stat)
      )
    }

    /** Performs a traversal with a given owner symbol. */
    def atOwner(owner: Symbol)(traverse: => Unit) {
      val prevOwner = currentOwner
      currentOwner = owner
      traverse
      currentOwner = prevOwner
    }

    /** Leave apply available in the generic traverser to do something else.
     */
    def apply[T <: Tree](tree: T): T = { traverse(tree); tree }
  }

  /** Delegates the traversal strategy to [[scala.reflect.internal.Trees]],
   *  because pattern matching on abstract types we have here degrades performance.
   */
  protected def itraverse(traverser: Traverser, tree: Tree): Unit = throw new MatchError(tree)

  /** Provides an extension hook for the traversal strategy.
   *  Future-proofs against new node types.
   */
  protected def xtraverse(traverser: Traverser, tree: Tree): Unit = throw new MatchError(tree)

  /** A class that implement a default tree transformation strategy: breadth-first component-wise cloning.
   */
  abstract class Transformer {
    /** The underlying tree copier. */
    val treeCopy: TreeCopier = newLazyTreeCopier

    /** The current owner symbol. */
    protected[scala] var currentOwner: Symbol = rootMirror.RootClass

    /** The enclosing method of the currently transformed tree. */
    protected def currentMethod = {
      def enclosingMethod(sym: Symbol): Symbol =
        if (sym.isMethod || sym == NoSymbol) sym else enclosingMethod(sym.owner)
      enclosingMethod(currentOwner)
    }

    /** The enclosing class of the currently transformed tree. */
    protected def currentClass = {
      def enclosingClass(sym: Symbol): Symbol =
        if (sym.isClass || sym == NoSymbol) sym else enclosingClass(sym.owner)
      enclosingClass(currentOwner)
    }

//    protected def currentPackage = currentOwner.enclosingTopLevelClass.owner

    /** Transforms a single tree. */
    def transform(tree: Tree): Tree = itransform(this, tree)

    /** Transforms a list of trees. */
    def transformTrees(trees: List[Tree]): List[Tree] = trees mapConserve (transform(_))

    /** Transforms a `Template`. */
    def transformTemplate(tree: Template): Template =
      transform(tree: Tree).asInstanceOf[Template]
    /** Transforms a list of `TypeDef` trees. */
    def transformTypeDefs(trees: List[TypeDef]): List[TypeDef] =
      trees mapConserve (tree => transform(tree).asInstanceOf[TypeDef])
    /** Transforms a `ValDef`. */
    def transformValDef(tree: ValDef): ValDef =
      if (tree.isEmpty) tree else transform(tree).asInstanceOf[ValDef]
    /** Transforms a list of `ValDef` nodes. */
    def transformValDefs(trees: List[ValDef]): List[ValDef] =
      trees mapConserve (transformValDef(_))
    /** Transforms a list of lists of `ValDef` nodes. */
    def transformValDefss(treess: List[List[ValDef]]): List[List[ValDef]] =
      treess mapConserve (transformValDefs(_))
    /** Transforms a list of `CaseDef` nodes. */
    def transformCaseDefs(trees: List[CaseDef]): List[CaseDef] =
      trees mapConserve (tree => transform(tree).asInstanceOf[CaseDef])
    /** Transforms a list of `Ident` nodes. */
    def transformIdents(trees: List[Ident]): List[Ident] =
      trees mapConserve (tree => transform(tree).asInstanceOf[Ident])
    /** Traverses a list of trees with a given owner symbol. */
    def transformStats(stats: List[Tree], exprOwner: Symbol): List[Tree] =
      stats mapConserve (stat =>
        if (exprOwner != currentOwner && stat.isTerm) atOwner(exprOwner)(transform(stat))
        else transform(stat)) filter (EmptyTree != _)
    /** Transforms `Modifiers`. */
    def transformModifiers(mods: Modifiers): Modifiers =
      mods.mapAnnotations(transformTrees)

    /** Transforms a tree with a given owner symbol. */
    def atOwner[A](owner: Symbol)(trans: => A): A = {
      val prevOwner = currentOwner
      currentOwner = owner
      val result = trans
      currentOwner = prevOwner
      result
    }
  }

  /** Delegates the transformation strategy to [[scala.reflect.internal.Trees]],
   *  because pattern matching on abstract types we have here degrades performance.
   */
  protected def itransform(transformer: Transformer, tree: Tree): Tree = throw new MatchError(tree)

  /** Provides an extension hook for the transformation strategy.
   *  Future-proofs against new node types.
   */
  protected def xtransform(transformer: Transformer, tree: Tree): Tree = throw new MatchError(tree)

  /** The type of tree modifiers. */
  type Modifiers >: Null <: AnyRef with ModifiersApi

  /** A tag that preserves the identity of the `Modifiers` abstract type from erasure.
   *  Can be used for pattern matching, instance tests, serialization and likes.
   */
  implicit val ModifiersTag: ClassTag[Modifiers]

  /** The API that all Modifiers support */
  abstract class ModifiersApi {
    /** The underlying flags of the enclosing definition.
     *  Is equal to `NoFlags` if none are specified explicitly.
     */
    def flags: FlagSet

    def hasFlag(flag: FlagSet): Boolean

    /** The visibility scope of the enclosing definition.
     *  Is equal to `tpnme.EMPTY` if none is specified explicitly.
     */
    def privateWithin: Name

    /** The annotations of the enclosing definition.
     *  Empty list if none are specified explicitly.
     */
    def annotations: List[Tree]

    /** Creates a new instance of `Modifiers` with
     *  the annotations transformed according to the given function.
     */
    def mapAnnotations(f: List[Tree] => List[Tree]): Modifiers =
      Modifiers(flags, privateWithin, f(annotations))
  }

  /** The constructor/deconstructor for `Modifiers` instances. */
  val Modifiers: ModifiersCreator

  /** An extractor class to create and pattern match with syntax `Modifiers(flags, privateWithin, annotations)`.
   *  Modifiers encapsulate flags, visibility annotations and Scala annotations for member definitions.
   */
  abstract class ModifiersCreator {
    def apply(): Modifiers = Modifiers(NoFlags, tpnme.EMPTY, List())
    def apply(flags: FlagSet, privateWithin: Name, annotations: List[Tree]): Modifiers
  }

  /** The factory for `Modifiers` instances. */
  def Modifiers(flags: FlagSet, privateWithin: Name): Modifiers = Modifiers(flags, privateWithin, List())

  /** The factory for `Modifiers` instances. */
  def Modifiers(flags: FlagSet): Modifiers = Modifiers(flags, tpnme.EMPTY)

  /** An empty `Modifiers` object: no flags, empty visibility annotation and no Scala annotations. */
  lazy val NoMods = Modifiers()
}
