package fr.cnrs.lacito.liftpatchbox.model;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.LiftDictionaryLanguagesManager;
import fr.cnrs.lacito.liftapi.model.AbstractExtensibleWithField;
import fr.cnrs.lacito.liftapi.model.AbstractExtensibleWithoutField;
import fr.cnrs.lacito.liftapi.model.AbstractIdentifiable;
import fr.cnrs.lacito.liftapi.model.AbstractLiftRoot;
import fr.cnrs.lacito.liftapi.model.AbstractNotable;
import fr.cnrs.lacito.liftapi.model.DuplicateTypeException;
import fr.cnrs.lacito.liftapi.model.Feature;
import fr.cnrs.lacito.liftapi.model.FeatureSet;
import fr.cnrs.lacito.liftapi.model.Form;
import fr.cnrs.lacito.liftapi.model.HasRefId;
import fr.cnrs.lacito.liftapi.model.GrammaticalInfo;
import fr.cnrs.lacito.liftapi.model.HasGrammaticalInfo;
import fr.cnrs.lacito.liftapi.model.LiftAnnotation;
import fr.cnrs.lacito.liftapi.model.LiftEntry;
import fr.cnrs.lacito.liftapi.model.LiftEtymology;
import fr.cnrs.lacito.liftapi.model.LiftExample;
import fr.cnrs.lacito.liftapi.model.LiftField;
import fr.cnrs.lacito.liftapi.model.FieldSpecification;
import fr.cnrs.lacito.liftapi.model.LiftFieldAndTraitDefinition;
import fr.cnrs.lacito.liftapi.model.LiftFieldAndTraitDefinitionDataModel;
import fr.cnrs.lacito.liftapi.model.LiftHeader;
import fr.cnrs.lacito.liftapi.model.LiftIllustration;
import fr.cnrs.lacito.liftapi.model.LiftMedia;
import fr.cnrs.lacito.liftapi.model.LiftNote;
import fr.cnrs.lacito.liftapi.model.LiftPronunciation;
import fr.cnrs.lacito.liftapi.model.LiftRelation;
import fr.cnrs.lacito.liftapi.model.LiftReversal;
import fr.cnrs.lacito.liftapi.model.LiftSense;
import fr.cnrs.lacito.liftapi.model.LiftTranslation;
import fr.cnrs.lacito.liftapi.model.LiftTrait;
import fr.cnrs.lacito.liftapi.model.LiftVariant;
import fr.cnrs.lacito.liftapi.model.MultiText;
import fr.cnrs.lacito.liftapi.model.TraitSpecification;
import fr.cnrs.lacito.liftapi.model.UndeclaredName;
import fr.cnrs.lacito.liftpatchbox.metamodel.LanguageKind;
import fr.cnrs.lacito.liftpatchbox.metamodel.Metamodel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import fr.cnrs.lacito.liftapi.change.ComponentAdded;
import fr.cnrs.lacito.liftapi.change.ComponentRemoved;
import fr.cnrs.lacito.liftapi.change.FormAdded;
import fr.cnrs.lacito.liftapi.change.FormRemoved;
import fr.cnrs.lacito.liftapi.change.ModelChange;
import fr.cnrs.lacito.liftapi.change.ValueChanged;
import java.util.function.IntConsumer;

/**
 * The single place that knows how the LiftPatch metamodel maps onto the
 * {@code lift-api} dictionary model.
 *
 * <p>Everything above this class — the resolver, the executor, plan mode — speaks
 * only of component types, property names and language codes, and never of
 * {@code LiftSense} or {@code MultiText}. That is what lets the engine be written
 * once against the metamodel, and what confines to one file the handful of places
 * where the two models do not line up.</p>
 *
 * <h2>The metamodel and the object model agree</h2>
 *
 * <p>Every component type, every property and every parentage the metamodel of
 * Appendix A declares has a home in the dictionary model, and nothing this class
 * is asked to do is refused for want of somewhere to put it. Three declarations of
 * the metamodel exist precisely so that this remains true, and changing any of
 * them would reopen a gap:</p>
 *
 * <ul>
 *   <li>{@code trait.type} and {@code field.type} are declared
 *       {@code "writable": false} (section 4.5). The dictionary model fixes the
 *       definition of a trait or a field when it is built, and that definition is
 *       the key its parent stores it under; for a trait it also gives the value a
 *       datatype. The model offers no way to change it ({@code LiftField}'s
 *       {@code specificationProperty()} is read-only, and there is no
 *       {@code retypeField}). The language therefore refuses to rewrite it with
 *       {@code PROPERTY_IS_READ_ONLY}, statically, rather than letting a command
 *       reach a component that cannot carry it out. A {@code note} and a
 *       {@code translation} are keyed by their type too, but theirs is writable:
 *       their parent re-keys them in place when {@code setType} is called;</li>
 *   <li>{@code variant} has a {@code form} and neither a {@code type} nor a
 *       {@code target}. The dictionary model, like LIFT, gives a variant no type,
 *       and its kind is an ordinary {@code variant-type} trait. The model's
 *       {@code ref} on a variant is outside the language: one read from a file is
 *       kept, and is unlinked and restored around a deletion like any inbound
 *       reference, but a script neither reads nor writes it;</li>
 *   <li>a {@code reversal} holds only sub-reversals, and its natural identity is
 *       {@code type + form} rather than {@code type + target}: the dictionary
 *       model gives a reversal forms, a type and nested reversals, and no
 *       reference, no trait, no annotation and no field.</li>
 * </ul>
 *
 * <p>{@link UnsupportedByModelException} therefore reports no condition a script
 * can reach. It survives as an internal guard, for a combination the static
 * validator should already have refused; reaching it is a defect of this library,
 * not of the script.</p>
 *
 * <h2>Mutation and rollback</h2>
 *
 * <p>Every mutation goes through this class, and through the public API of the
 * dictionary model, which reports each change it makes. The {@link Journal}
 * records those changes, and reverts or reapplies them: that is what makes a
 * script atomic without copying the dictionary (Part 2, section 12.2), and what
 * lets a timeline move through what a series of scripts did.</p>
 */
public final class LiftModel {

    private final LiftDictionary dictionary;
    private final Metamodel metamodel;
    private final Journal journal;
    private final HomophoneNumbering homophones;

    /**
     * An adapter over a dictionary.
     *
     * @param dictionary the dictionary to read and write
     * @param metamodel  the metamodel the script is validated against
     * @param journal    the journal recording the changes of the dictionary; what this
     *                   adapter derives from the dictionary is invalidated whenever the
     *                   journal reverts or reapplies changes
     */
    public LiftModel(LiftDictionary dictionary, Metamodel metamodel, Journal journal) {
        this.dictionary = dictionary;
        this.metamodel = metamodel;
        this.homophones = new HomophoneNumbering(dictionary);
        this.journal = journal;
        journal.afterReplay(homophones::invalidate);
        journal.attributeWith(LiftModel::entriesChangedBy);
    }

    /**
     * The entries whose subtree a change changed, worked out when the change is made:
     * the entry a component, a form or a value belongs to, the parent's for a
     * component attached or removed, and the entry itself for an entry added or
     * removed. A change of the header or of the language lists belongs to none.
     *
     * @param change the change, just made
     * @return the entries, none or one
     */
    static Set<LiftEntry> entriesChangedBy(ModelChange change) {
        Object where = switch (change) {
            case ComponentAdded a -> a.subject() instanceof LiftEntry e ? e : a.parent();
            case ComponentRemoved r -> r.subject() instanceof LiftEntry e ? e : r.parent();
            case FormAdded f -> f.text().getParent();
            case FormRemoved f -> f.text().getParent();
            case ValueChanged<?> v -> v.owner() instanceof Form form
                ? (form.getMultiText() == null ? null : form.getMultiText().getParent())
                : v.owner();
            default -> null;
        };
        AbstractLiftRoot node = where instanceof AbstractLiftRoot root ? root : null;
        while (node != null && !(node instanceof LiftEntry)) {
            node = node.getParentNode();
        }
        return node == null ? Set.of() : Set.of((LiftEntry) node);
    }

    /**
     * The dictionary this adapter writes to.
     *
     * @return the dictionary
     */
    public LiftDictionary dictionary() {
        return dictionary;
    }

    /**
     * The root of the hierarchy, the one legal parent of an {@code entry}.
     *
     * @return the root handle
     */
    public ComponentRef root() {
        return new ComponentRef.Root(dictionary);
    }

    // ===================================================================
    // Languages
    // ===================================================================

    /**
     * The language manager of one language kind.
     *
     * @param kind the language kind
     * @return the dictionary's manager for that kind
     */
    public LiftDictionaryLanguagesManager languages(LanguageKind kind) {
        return kind == LanguageKind.OBJECT
            ? dictionary.getObjectLanguageManager()
            : dictionary.getMetaLanguageManager();
    }

    /**
     * Whether the dictionary declares a language of the given kind.
     *
     * @param kind the language kind
     * @param code the language code
     * @return {@code true} when the code is in that kind's list
     */
    public boolean hasLanguage(LanguageKind kind, String code) {
        return languages(kind).hasLanguage(code);
    }

    /**
     * The first language of a kind, which is the fallback default when no binding
     * is in scope (Part 1, section 2.1).
     *
     * @param kind the language kind
     * @return the first code of that kind's list, or {@code null} when the list is empty
     */
    public String firstLanguage(LanguageKind kind) {
        Set<String> all = languages(kind).getLanguages();
        return all.isEmpty() ? null : all.iterator().next();
    }

    /**
     * Add a language to the dictionary.
     *
     * @param kind the language kind
     * @param code the language code
     */
    public void createLanguage(LanguageKind kind, String code) {
        LiftDictionaryLanguagesManager manager = languages(kind);
        manager.addLanguage(code);
    }

    /**
     * The number of languages of a kind.
     *
     * @param kind the language kind
     * @return the size of that kind's list
     */
    public int languageCount(LanguageKind kind) {
        return languages(kind).getLanguages().size();
    }

    /**
     * Whether a text of the dictionary is written in a language, a text of the header
     * (a feature label...) included.
     *
     * @param kind the language kind
     * @param code the language code
     * @return {@code true} when the language has at least one occurrence
     */
    public boolean languageInUse(LanguageKind kind, String code) {
        return languages(kind).getLanguageOccurrence(code) != 0;
    }

    /**
     * Remove a language from the dictionary.
     *
     * @param kind the language kind
     * @param code the language code, which no text uses
     */
    public void deleteLanguage(LanguageKind kind, String code) {
        LiftDictionaryLanguagesManager manager = languages(kind);
        manager.removeLanguage(code);
    }

    // ===================================================================
    // Declarations: feature sets, features, field and trait definitions
    // ===================================================================

    /**
     * The feature sets every dictionary has, and that a script can neither delete nor
     * rename (Part 1, section 2.1.4). The dictionary model refuses them too, but has
     * no query for them.
     */
    private static final Set<String> BUILT_IN_FEATURE_SETS = Set.of(
        "grammatical-info", "note-type", "translation-type", "lexical-relation",
        "reversal-type", "annotation-type", "etymology");

    private LiftHeader header() {
        return dictionary.getHeader();
    }

    /**
     * Whether the header declares a feature set.
     *
     * @param id the id of the feature set
     * @return {@code true} when it exists
     */
    public boolean hasFeatureSet(String id) {
        return header().hasFeatureSet(id);
    }

    /**
     * Whether a feature set is one of the seven every dictionary has.
     *
     * @param id the id of the feature set
     * @return {@code true} for a built-in feature set
     */
    public boolean isBuiltInFeatureSet(String id) {
        return BUILT_IN_FEATURE_SETS.contains(id);
    }

    /**
     * Whether a feature set is defined in an external ranges file: such a set is
     * read-only, since that file is never written when the dictionary is saved.
     *
     * @param id the id of an existing feature set
     * @return {@code true} when its content comes from an external ranges file
     */
    public boolean isExternal(String id) {
        return header().getFeatureSet(id).isExternallyDefined();
    }

    /**
     * Make a feature set writable, declaring it when it is a built-in set the file
     * does not declare (Part 1, section 2.1.4).
     *
     * @throws ExternalRangeException if the set is defined in an external ranges file
     */
    private void requireWritable(FeatureSet fs) {
        if (fs.isExternallyDefined()) {
            throw new ExternalRangeException(fs.getId(), null);
        }
        if (!fs.isDeclared()) {
            header().declareFeatureSet(fs);
        }
    }

    /**
     * The feature a command names as a value (a {@code type}, the {@code value} of a
     * category, a value of an option trait), declared: a value its feature set does not
     * declare is declared, and so is the set itself when it is a built-in set the file
     * does not declare (Part 1, section 2.1.4).
     *
     * <p>A feature set defined in an external ranges file cannot be changed: a value
     * it already holds is used as it is, and a new one is refused.</p>
     *
     * @param fs the feature set the value is taken from
     * @param id the id of the value
     * @return the feature
     * @throws ExternalRangeException for a new value of an external feature set
     */
    private Feature featureFor(FeatureSet fs, String id) {
        if (fs.hasFeature(id)) {
            Feature existing = fs.getFeature(id);
            if (!existing.isDeclared() && !fs.isExternallyDefined()) {
                header().declareFeature(existing);
            }
            return existing;
        }
        if (fs.isExternallyDefined()) {
            throw new ExternalRangeException(fs.getId(), id);
        }
        if (!fs.isDeclared()) {
            header().declareFeatureSet(fs);
        }
        return fs.addFeature(id);
    }

    /**
     * Declare a feature set.
     *
     * @param id the id of the new feature set
     */
    public void createFeatureSet(String id) {
        FeatureSet fs = header().addFeatureSet(id);
    }

    /**
     * Remove a feature set with its features.
     *
     * <p>Whether the set is in use is the dictionary model's question: a definition
     * taking it as its range, or one of its features used outside the set. What the
     * set holds itself goes with it.</p>
     *
     * @param id the id of the feature set
     * @throws IllegalStateException if the set is in use; nothing has changed then
     */
    public void deleteFeatureSet(String id) {
        FeatureSet fs = header().getFeatureSet(id);
        header().removeFeatureSet(fs);
    }

    /**
     * Rename a feature set. What refers to the set holds
     * the object, and follows.
     *
     * @param id    the current id
     * @param newId the new id
     */
    public void renameFeatureSet(String id, String newId) {
        FeatureSet fs = header().getFeatureSet(id);
        header().renameFeatureSet(fs, newId);
    }

    /**
     * Whether a feature set holds a feature.
     *
     * @param featureSet the id of an existing feature set
     * @param id         the id of the feature
     * @return {@code true} when it exists
     */
    public boolean hasFeature(String featureSet, String id) {
        return header().getFeatureSet(featureSet).hasFeature(id);
    }

    /**
     * Whether something in the dictionary uses a feature (see
     * {@code LiftHeader.isUsed(Feature)}).
     *
     * @param featureSet the id of the feature set
     * @param id         the id of the feature
     * @return {@code true} when the feature is in use
     */
    public boolean featureInUse(String featureSet, String id) {
        return header().isUsed(header().getFeatureSet(featureSet).getFeature(id));
    }

    /**
     * Whether a feature set holds a feature its range declares. A value the
     * dictionary uses but its range does not declare is held too, undeclared
     * ({@link #hasFeature(String, String)}).
     *
     * @param featureSet the id of an existing feature set
     * @param id         the id of the feature
     * @return {@code true} when it exists and is declared
     */
    public boolean hasDeclaredFeature(String featureSet, String id) {
        FeatureSet fs = header().getFeatureSet(featureSet);
        return fs.hasFeature(id) && fs.getFeature(id).isDeclared();
    }

    /**
     * Declare a feature in a feature set: a new one, or one the
     * dictionary uses but its range does not declare. A built-in set the file does not
     * declare is declared with it.
     *
     * @param featureSet the id of the feature set, which is not external
     * @param id         the id of the feature
     */
    public void createFeature(String featureSet, String id) {
        featureFor(header().getFeatureSet(featureSet), id);
    }

    /**
     * Remove a feature.
     *
     * @param featureSet the id of the feature set
     * @param id         the id of a feature no component uses
     */
    public void deleteFeature(String featureSet, String id) {
        FeatureSet fs = header().getFeatureSet(featureSet);
        requireWritable(fs);
        Feature f = fs.removeFeature(id);
    }

    /**
     * Rename a feature. Every component typed by the
     * feature, every category and every trait value holding it now reads the new id:
     * they hold the {@code Feature} object, which compares by identity.
     *
     * @param featureSet the id of the feature set
     * @param id         the current id of the feature
     * @param newId      its new id
     */
    public void renameFeature(String featureSet, String id, String newId) {
        FeatureSet fs = header().getFeatureSet(featureSet);
        // A value the dictionary uses but its range does not declare is declared first:
        // renaming it is a change of the header.
        Feature f = featureFor(fs, id);
        fs.changeFeatureId(f, newId);
    }

    /**
     * Whether the header declares a field or trait definition under a name, of
     * either kind: the two share one namespace.
     *
     * @param name the name of the definition
     * @return {@code true} when one exists
     */
    public boolean hasDefinition(String name) {
        return header().containsFieldsAndTraitsDefinitions(name);
    }

    /**
     * Whether the definition under a name can be the one a {@code field-delete} (or a
     * {@code trait-delete}) names: one of that kind, or one whose kind the file left
     * undeclared.
     *
     * @param name  the name of an existing definition
     * @param field {@code true} for a field, {@code false} for a trait
     * @return {@code false} when it is a definition of the other kind
     */
    public boolean definitionIsOfKind(String name, boolean field) {
        LiftFieldAndTraitDefinition fd = header().getFieldsAndTraitsDefinitions(name);
        return field ? !fd.isTraitDefinition() : !fd.isFieldDefinition();
    }

    /**
     * Whether a field or a trait uses a definition, in an entry or in the header.
     *
     * @param name the name of an existing definition
     * @return {@code true} when it is in use
     */
    public boolean definitionInUse(String name) {
        return header().isUsed(header().getFieldsAndTraitsDefinitions(name));
    }

    /**
     * Declare a field or trait definition.
     *
     * @param field {@code true} for a field, {@code false} for a trait
     * @param name  the name of the new definition
     * @param model the data model as LIFT names it; already checked against the kind
     * @param range the id of the feature set of an option model, or {@code null}
     */
    public void createDefinition(boolean field, String name, String model, String range) {
        LiftFieldAndTraitDefinition fd = field
            ? header().createFieldDefinition(name)
            : header().createTraitDefinition(name);
        fd.setDataModel(Optional.of(model));
        if (range != null) {
            fd.setResolvedRange(Optional.of(header().getFeatureSet(range)));
        }
    }

    /**
     * Remove a field or trait definition no field or trait uses.
     *
     * @param name the name of the definition
     */
    public void deleteDefinition(String name) {
        LiftFieldAndTraitDefinition fd = header().getFieldsAndTraitsDefinitions(name);
        header().removeFieldOrTraitDefinition(fd);
    }

    // ===================================================================
    // Identity and position
    // ===================================================================

    /**
     * The persistent identifier of a component, which only an {@code entry} and a
     * {@code sense} carry.
     *
     * @param ref the component
     * @return the identifier, or empty when the component has none
     */
    public Optional<String> idOf(ComponentRef ref) {
        if (ref instanceof ComponentRef.Node node
            && node.node() instanceof AbstractIdentifiable identifiable) {
            return identifiable.getId();
        }
        return Optional.empty();
    }

    /**
     * The homophone number of an entry (Part 1, section 5.3.2.1).
     *
     * @param ref the entry
     * @return its homophone number, an integer greater than or equal to 1
     */
    public int hnOf(ComponentRef ref) {
        return homophones.numberOf((LiftEntry) ((ComponentRef.Node) ref).node());
    }

    /**
     * Forget the cached homophone numbers, which a command changing a {@code form}
     * may have invalidated.
     */
    public void invalidateHomophones() {
        homophones.invalidate();
    }

    /**
     * The 1-based position of a component among its same-type siblings.
     *
     * @param ref the component
     * @return the position, or empty for an {@code entry}, a typed component and a
     *         singleton, none of which has a position this language reports
     */
    public Optional<Integer> positionOf(ComponentRef ref) {
        if (!(ref instanceof ComponentRef.Node node)) {
            return Optional.empty();
        }
        String type = node.componentType();
        if (Metamodel.ENTRY.equals(type)
            || !metamodel.requireComponentType(type).isOrdered()) {
            return Optional.empty();
        }
        ComponentRef parent = parentOf(ref);
        if (parent == null) {
            return Optional.empty();
        }
        List<ComponentRef> siblings = children(parent, type);
        int index = siblings.indexOf(ref);
        return index < 0 ? Optional.empty() : Optional.of(index + 1);
    }

    /**
     * The parent of a component.
     *
     * @param ref the component
     * @return its parent, the root for an {@code entry}, or {@code null} when the
     *         component is detached
     */
    public ComponentRef parentOf(ComponentRef ref) {
        return switch (ref) {
            case ComponentRef.Root _ -> null;
            case ComponentRef.Category c -> new ComponentRef.Node("sense", c.host());
            case ComponentRef.Node node -> {
                if (node.node() instanceof LiftEntry) {
                    yield root();
                }
                AbstractLiftRoot parent = node.node().getParentNode();
                yield parent == null ? null : new ComponentRef.Node(typeOf(parent), parent);
            }
        };
    }

    /**
     * The LiftPatch component type of a dictionary-model component.
     *
     * @param node the component
     * @return the component type name
     * @throws UnsupportedByModelException if the component is of a class this
     *         metamodel does not name
     */
    public static String typeOf(AbstractLiftRoot node) {
        return switch (node) {
            case LiftEntry _ -> "entry";
            case LiftSense _ -> "sense";
            case LiftExample _ -> "example";
            case LiftEtymology _ -> "etymology";
            case LiftVariant _ -> "variant";
            case LiftRelation _ -> "relation";
            case LiftIllustration _ -> "illustration";
            case LiftMedia _ -> "media";
            case LiftPronunciation _ -> "pronunciation";
            case LiftReversal _ -> "reversal";
            case LiftTrait _ -> "trait";
            case LiftAnnotation _ -> "annotation";
            case LiftNote _ -> "note";
            case LiftField _ -> "field";
            case LiftTranslation _ -> "translation";
            case GrammaticalInfo _ -> "category";
            default -> throw new UnsupportedByModelException(
                "the dictionary model class " + node.getClass().getSimpleName()
                    + " has no LiftPatch component type");
        };
    }

    // ===================================================================
    // Children
    // ===================================================================

    /**
     * The children of one component type under a parent, in the order the language
     * designates them by: the sibling list for an ordered type, the map's keys
     * sorted for a typed one, and at most one for a singleton.
     *
     * @param parent    the parent component
     * @param childType the child component type
     * @return the children, never {@code null}
     * @throws UnsupportedByModelException if the dictionary model cannot hold that
     *         child under that parent
     */
    public List<ComponentRef> children(ComponentRef parent, String childType) {
        if (parent instanceof ComponentRef.Root) {
            if (!Metamodel.ENTRY.equals(childType)) {
                return List.of();
            }
            List<ComponentRef> out = new ArrayList<>();
            for (LiftEntry e : dictionary.getLiftDictionaryRegistry().getEntries()) {
                out.add(new ComponentRef.Node("entry", e));
            }
            return out;
        }
        if (parent instanceof ComponentRef.Category category) {
            if ("trait".equals(childType)) {
                return grammaticalInfo(category.host())
                    .map(g -> wrap("trait", g.getTraits()))
                    .orElse(List.of());
            }
            return List.of();
        }
        AbstractLiftRoot node = ((ComponentRef.Node) parent).node();
        return switch (childType) {
            case "sense" -> node instanceof LiftEntry e ? wrap("sense", e.getSenses())
                : node instanceof LiftSense s ? wrap("sense", s.getSenses())
                : unsupportedChild(parent, childType);
            case "example" -> node instanceof LiftSense s ? wrap("example", s.getExamples())
                : unsupportedChild(parent, childType);
            case "etymology" -> node instanceof LiftEntry e ? wrap("etymology", e.getEtymologies())
                : unsupportedChild(parent, childType);
            case "variant" -> node instanceof LiftEntry e ? wrap("variant", e.getVariants())
                : unsupportedChild(parent, childType);
            case "relation" -> node instanceof LiftEntry e ? wrap("relation", e.getRelations())
                : node instanceof LiftSense s ? wrap("relation", s.getRelations())
                : node instanceof LiftVariant v ? wrap("relation", v.getRelations())
                : unsupportedChild(parent, childType);
            case "pronunciation" -> node instanceof LiftEntry e
                ? wrap("pronunciation", e.getPronunciations())
                : node instanceof LiftVariant v ? wrap("pronunciation", v.getPronunciations())
                : unsupportedChild(parent, childType);
            case "illustration" -> node instanceof LiftSense s
                ? wrap("illustration", s.getIllustrations())
                : unsupportedChild(parent, childType);
            case "media" -> node instanceof LiftPronunciation p ? wrap("media", p.getMedias())
                : unsupportedChild(parent, childType);
            case "reversal" -> node instanceof LiftSense s ? wrap("reversal", s.getReversals())
                : node instanceof LiftReversal r ? wrap("reversal", r.getReversals())
                : unsupportedChild(parent, childType);
            case "trait" -> node instanceof AbstractExtensibleWithoutField e
                ? wrap("trait", e.getTraits())
                : unsupportedChild(parent, childType);
            case "annotation" -> node instanceof AbstractExtensibleWithoutField e
                ? wrap("annotation", e.getAnnotations())
                : node instanceof LiftTrait t ? wrap("annotation", t.getAnnotations())
                : unsupportedChild(parent, childType);
            case "note" -> node instanceof AbstractNotable n
                ? sortedByFeature("note", n.getNotes())
                : unsupportedChild(parent, childType);
            case "field" -> node instanceof AbstractExtensibleWithField f
                ? sortedByKey("field", f.getFields())
                : unsupportedChild(parent, childType);
            case "translation" -> node instanceof LiftExample x
                ? wrap("translation", List.copyOf(x.getTranslationComponents()))
                : unsupportedChild(parent, childType);
            case "category" -> node instanceof LiftSense s
                ? List.of(new ComponentRef.Category(s))
                : unsupportedChild(parent, childType);
            default -> List.of();
        };
    }

    private static List<ComponentRef> wrap(String type, List<? extends AbstractLiftRoot> nodes) {
        List<ComponentRef> out = new ArrayList<>(nodes.size());
        for (AbstractLiftRoot node : nodes) {
            out.add(new ComponentRef.Node(type, node));
        }
        return out;
    }

    /**
     * The children of a typed component type whose parent keys them by a string:
     * the {@code field} map. They are returned in key order, which is stable and
     * carries no meaning — a typed component has no position.
     */
    private static List<ComponentRef> sortedByKey(
        String type, Map<String, ? extends AbstractLiftRoot> map
    ) {
        List<ComponentRef> out = new ArrayList<>(map.size());
        map.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> out.add(new ComponentRef.Node(type, e.getValue())));
        return out;
    }

    /**
     * The same, for a parent that keys its children by the {@link Feature} their
     * type is: the {@code note} map.
     */
    private static List<ComponentRef> sortedByFeature(
        String type, Map<Feature, ? extends AbstractLiftRoot> map
    ) {
        List<ComponentRef> out = new ArrayList<>(map.size());
        map.entrySet().stream()
            .sorted(Comparator.comparing(e -> e.getKey().getId()))
            .forEach(e -> out.add(new ComponentRef.Node(type, e.getValue())));
        return out;
    }

    private static List<ComponentRef> unsupportedChild(ComponentRef parent, String childType) {
        throw new UnsupportedByModelException(
            "the dictionary model holds no `" + childType + "` under a `"
                + parent.componentType() + "`");
    }

    private static Optional<GrammaticalInfo> grammaticalInfo(LiftSense sense) {
        return sense.getGrammaticalInfo();
    }

    // ===================================================================
    // Reading properties
    // ===================================================================

    /**
     * The value of a scalar property.
     *
     * @param ref      the component
     * @param property the property name
     * @return the value, or empty when the property is unset
     * @throws UnsupportedByModelException if the dictionary model has no field for it
     */
    public Optional<String> scalar(ComponentRef ref, String property) {
        return switch (ref) {
            case ComponentRef.Category category -> "value".equals(property)
                ? grammaticalInfo(category.host())
                    .map(GrammaticalInfo::getGramInfoValue)
                    .map(Feature::getId)
                : Optional.empty();
            case ComponentRef.Node node -> scalarOfNode(node, property);
            case ComponentRef.Root _ -> Optional.empty();
        };
    }

    private Optional<String> scalarOfNode(ComponentRef.Node ref, String property) {
        AbstractLiftRoot node = ref.node();
        return switch (node) {
            case LiftEntry _ -> Optional.empty();
            case LiftEtymology y -> switch (property) {
                case "type" -> featureId(y.getType());
                default -> Optional.empty();
            };
            case LiftRelation r -> switch (property) {
                case "type" -> featureId(r.getType());
                case "target" -> reference(r.getRefId(), r.getRefObject());
                default -> Optional.empty();
            };
            case LiftReversal l -> "type".equals(property)
                ? featureId(l.getType()) : Optional.empty();
            case LiftIllustration i -> "url".equals(property)
                ? Optional.ofNullable(i.getHref()) : Optional.empty();
            case LiftMedia m -> "url".equals(property)
                ? Optional.ofNullable(m.getHref()) : Optional.empty();
            case LiftTrait t -> switch (property) {
                case "type" -> Optional.of(t.getName());
                case "value" -> Optional.ofNullable(t.getValue()).filter(s -> !s.isEmpty());
                default -> Optional.empty();
            };
            case LiftAnnotation a -> switch (property) {
                case "type" -> featureId(a.getType());
                case "value" -> Optional.ofNullable(a.getValue());
                case "when" -> Optional.ofNullable(a.getWhen());
                case "who" -> Optional.ofNullable(a.getWho());
                default -> Optional.empty();
            };
            case LiftNote n -> "type".equals(property) ? featureId(n.getType()) : Optional.empty();
            case LiftTranslation t -> "type".equals(property)
                ? featureId(t.getType()) : Optional.empty();
            case LiftField f -> "type".equals(property)
                ? Optional.of(f.getName())
                : Optional.empty();
            case GrammaticalInfo g -> "value".equals(property)
                ? featureId(g.getGramInfoValue()) : Optional.empty();
            default -> Optional.empty();
        };
    }

    /**
     * The identifier a reference points at.
     *
     * <p>The dictionary model stores a reference in either of two places: the
     * builders set the referenced <em>object</em>, and the XML reader sets the
     * <em>identifier</em>. Both are read here, identifier first, which is the order
     * the model's own registration code uses.</p>
     */
    private static Optional<String> reference(
        Optional<String> refId, AbstractIdentifiable refObject
    ) {
        if (refId.isPresent()) {
            return refId;
        }
        return refObject == null ? Optional.empty() : refObject.getId();
    }

    private static Optional<String> featureId(Feature feature) {
        return feature == null ? Optional.empty() : Optional.of(feature.getId());
    }

    /**
     * The multitext of a property.
     *
     * @param ref      the component
     * @param property the property name
     * @return the multitext, or empty when the component has no such property
     * @throws UnsupportedByModelException if the dictionary model has no field for it
     */
    public Optional<MultiText> multitext(ComponentRef ref, String property) {
        if (ref instanceof ComponentRef.Category) {
            return Optional.empty();
        }
        if (!(ref instanceof ComponentRef.Node node)) {
            return Optional.empty();
        }
        AbstractLiftRoot n = node.node();
        return switch (n) {
            case LiftEntry e -> "form".equals(property)
                ? Optional.of(e.getForms()) : Optional.empty();
            case LiftSense s -> switch (property) {
                case "gloss" -> Optional.of(s.getGlosses());
                case "definition" -> Optional.of(s.getDefinition());
                default -> Optional.empty();
            };
            case LiftExample x -> "text".equals(property)
                ? Optional.of(x.getExample()) : Optional.empty();
            case LiftEtymology y -> switch (property) {
                case "form" -> Optional.of(y.getForms());
                case "gloss" -> Optional.of(y.getGlosses());
                default -> Optional.empty();
            };
            case LiftVariant v -> "form".equals(property)
                ? Optional.of(v.getForms()) : Optional.empty();
            case LiftReversal l -> "form".equals(property)
                ? Optional.of(l.getForms()) : Optional.empty();
            case LiftIllustration i -> "label".equals(property)
                ? Optional.of(i.getLabel()) : Optional.empty();
            case LiftMedia m -> "label".equals(property)
                ? Optional.of(m.getLabel()) : Optional.empty();
            case LiftPronunciation p -> "transcription".equals(property)
                ? Optional.of(p.getPronunciation()) : Optional.empty();
            case LiftAnnotation a -> "comment".equals(property)
                ? Optional.of(a.getText()) : Optional.empty();
            case LiftNote note -> "text".equals(property)
                ? Optional.of(note.getText()) : Optional.empty();
            case LiftField f -> "text".equals(property)
                ? Optional.of(f.getText()) : Optional.empty();
            case LiftTranslation t -> "text".equals(property)
                ? Optional.of(t.getTranslation()) : Optional.empty();
            default -> Optional.empty();
        };
    }

    /**
     * One qualified value of a multitext property.
     *
     * @param ref      the component
     * @param property the property name
     * @param language the language code
     * @return the value, or empty when it is unset
     */
    public Optional<String> qualified(ComponentRef ref, String property, String language) {
        return multitext(ref, property)
            .flatMap(m -> m.getForm(language))
            .map(Form::toPlainText);
    }

    /**
     * The languages a multitext property has a value for.
     *
     * @param ref      the component
     * @param property the property name
     * @return the language codes, in the dictionary model's order
     */
    public Set<String> languagesOf(ComponentRef ref, String property) {
        return multitext(ref, property)
            .map(m -> (Set<String>) new LinkedHashSet<>(m.getLangs()))
            .orElse(Set.of());
    }

    // ===================================================================
    // Writing properties
    // ===================================================================

    /**
     * Write a scalar property.
     *
     * @param ref      the component
     * @param property the property name
     * @param value    the new value, or {@code null} to clear it
     * @throws UnsupportedByModelException if the dictionary model cannot store it
     */
    public void setScalar(ComponentRef ref, String property, String value) {
        if (ref instanceof ComponentRef.Category category) {
            if (!"value".equals(property)) {
                throw unsupportedProperty("category", property);
            }
            setCategoryValue(category.host(), value);
            return;
        }
        AbstractLiftRoot node = ((ComponentRef.Node) ref).node();
        switch (node) {
            case LiftEntry _ -> throw unsupportedProperty("entry", property);
            case LiftEtymology y -> {
                switch (property) {
                    case "type" -> setFeature(value, dictionary.getHeader().getEtymologyTypeManager(),
                        f -> y.setType(f));
                    case "source" -> y.setSource(value);
                    default -> throw unsupportedProperty("etymology", property);
                }
            }
            case LiftRelation r -> {
                switch (property) {
                    case "type" -> setFeature(value, dictionary.getHeader().getRelationTypeManager(),
                        f -> r.setType(f));
                    case "target" -> setReference(value, r::setRefId, r::setRefObject);
                    default -> throw unsupportedProperty("relation", property);
                }
            }
            case LiftReversal l -> {
                if (!"type".equals(property)) {
                    throw unsupportedProperty("reversal", property);
                }
                setFeature(value, dictionary.getHeader().getInverseTypeManager(),
                    f -> l.setType(f));
            }
            case LiftTrait t -> {
                switch (property) {
                    case "value" -> {
                        t.setTypedValue(traitValue(t.getSpecification(), value));
                    }
                    case "type" -> throw new UnsupportedByModelException(
                        "`trait.type` is read-only (section 4.5) and the validator refuses a "
                            + "command that writes it; reaching this point is a defect");
                    default -> throw unsupportedProperty("trait", property);
                }
            }
            case LiftAnnotation a -> {
                switch (property) {
                    case "type" -> setFeature(value, dictionary.getHeader().getAnnotationTypeManager(),
                        f -> a.setType(f));
                    case "value" -> {
                        a.setValue(value);
                    }
                    case "when" -> {
                        a.setWhen(value);
                    }
                    case "who" -> {
                        a.setWho(value);
                    }
                    default -> throw unsupportedProperty("annotation", property);
                }
            }
            case LiftNote n -> {
                if (!"type".equals(property)) {
                    throw unsupportedProperty("note", property);
                }
                setFeature(value,
                    dictionary.getHeader().getNoteTypeManager(), n::setType);
            }
            case LiftTranslation t -> {
                if (!"type".equals(property)) {
                    throw unsupportedProperty("translation", property);
                }
                setFeature(value,
                    dictionary.getHeader().getTranslationTypeManager(), t::setType);
            }
            case LiftField _ -> {
                if ("type".equals(property)) {
                    throw new UnsupportedByModelException(
                        "`field.type` is read-only (section 4.5) and the validator refuses a "
                            + "command that writes it; reaching this point is a defect");
                }
                throw unsupportedProperty("field", property);
            }
            case GrammaticalInfo g -> {
                if (!"value".equals(property)) {
                    throw unsupportedProperty("category", property);
                }
                setCategoryValue(g.getParent(), value);
            }
            case LiftIllustration i -> {
                if (!"url".equals(property)) {
                    throw unsupportedProperty("illustration", property);
                }
                i.setHref(value);
            }
            case LiftMedia m -> {
                if (!"url".equals(property)) {
                    throw unsupportedProperty("media", property);
                }
                m.setHref(value);
            }
            default -> throw unsupportedProperty(ref.componentType(), property);
        }
    }

    /**
     * Write one qualified value of a multitext property.
     *
     * @param ref      the component
     * @param property the property name
     * @param language the language code
     * @param value    the new text
     */
    public void setQualified(ComponentRef ref, String property, String language, String value) {
        MultiText multitext = multitext(ref, property)
            .orElseThrow(() -> unsupportedProperty(ref.componentType(), property));
        if (multitext.containsLang(language)) {
            multitext.removeForm(language);
        }
        multitext.add(new Form(language, value));
    }

    /**
     * Remove one qualified value of a multitext property.
     *
     * @param ref      the component
     * @param property the property name
     * @param language the language code
     */
    public void removeQualified(ComponentRef ref, String property, String language) {
        MultiText multitext = multitext(ref, property)
            .orElseThrow(() -> unsupportedProperty(ref.componentType(), property));
        if (!multitext.containsLang(language)) {
            return;
        }
        multitext.removeForm(language);
    }

    /**
     * Write a reference, setting both the identifier and the referenced object.
     *
     * <p>Both are set because the dictionary model reads each of them in a
     * different place: its registration code prefers the identifier, and its
     * removal code dereferences the object.</p>
     */
    private void setReference(
        String value,
        java.util.function.Consumer<String> idSetter,
        java.util.function.Consumer<AbstractIdentifiable> objectSetter
    ) {
        AbstractIdentifiable target = value == null
            ? null
            : dictionary.getLiftDictionaryRegistry().getEntryOrSenseByLiftId(value);
        idSetter.accept(value);
        if (target != null) {
            objectSetter.accept(target);
        }
    }

    /**
     * Write the {@code type} of a component.
     *
     * <p>On a component whose parent holds its children keyed by type — a
     * {@code note}, a {@code translation} — {@code setType} delegates to that
     * parent's {@code retypeX}, which re-keys the child in place: the component
     * keeps its identity, its text and its registration. A duplicate key is refused by the dictionary model
     * with a {@link DuplicateTypeException}, which
     * {@link #setScalar(ComponentRef, String, String)}'s caller turns into the
     * language's own {@code CANNOT_CREATE_DUPLICATE}.</p>
     */
    private void setFeature(
        String value, FeatureSet manager, java.util.function.Consumer<Feature> setter
    ) {
        Feature fresh = value == null ? null : featureFor(manager, value);
        setter.accept(fresh);
    }

    /**
     * Write the part of speech of a component that carries a grammatical
     * information: a sense, which is the host of the {@code category} singleton, or
     * a reversal, since lift-api 0.1.1 gives a reversal one too.
     */
    private void setCategoryValue(HasGrammaticalInfo host, String value) {
        Optional<GrammaticalInfo> old = host.getGrammaticalInfo();
        if (value == null) {
            if (old.isPresent()) {
                host.deleteGrammaticalInfo();
            }
            return;
        }
        Feature feature = featureFor(header().getGrammaticalInfoManager(), value);
        if (old.isPresent()) {
            host.deleteGrammaticalInfo();
        }
        host.setGrammaticalInfo(feature);
    }

    /**
     * The typed value of a trait, parsed after what its name refers to, as the
     * dictionary model's own reader does. The values of an option trait are features
     * of its range: each is declared first ({@link #featureFor(FeatureSet, String)}),
     * the parser then finds them.
     */
    private Object traitValue(TraitSpecification specification, String value) {
        String text = value == null ? "" : value;
        specification.getResolvedFeatureSet().ifPresent(range -> {
            LiftFieldAndTraitDefinitionDataModel model = specification.getDataModel();
            if (model == LiftFieldAndTraitDefinitionDataModel.FEATURE) {
                if (!text.isEmpty()) {
                    featureFor(range, text);
                }
            } else if (model == LiftFieldAndTraitDefinitionDataModel.FEATURE_SET
                || model == LiftFieldAndTraitDefinitionDataModel.FEATURE_LIST) {
                // The separator of the dictionary model's parser.
                for (String id : text.split(",")) {
                    if (!id.trim().isEmpty()) {
                        featureFor(range, id.trim());
                    }
                }
            }
        });
        return LiftTrait.parseValue(specification, text);
    }

    // ===================================================================
    // Creating, deleting and moving components
    // ===================================================================

    /**
     * What a {@code create} or the create branch of an {@code upsert} asks for: a
     * component type and its resolved initializers.
     *
     * @param componentType the component type to create
     * @param scalars       the scalar properties, by name
     * @param multitexts    the multitext properties, by name and then by language
     */
    public record NewComponent(
        String componentType,
        Map<String, String> scalars,
        Map<String, Map<String, String>> multitexts
    ) {

        /**
         * Canonical constructor, taking unmodifiable copies of the maps.
         *
         * @param componentType the component type to create
         * @param scalars       the scalar properties
         * @param multitexts    the multitext properties
         */
        public NewComponent {
            scalars = Map.copyOf(scalars);
            Map<String, Map<String, String>> copy = new LinkedHashMap<>();
            multitexts.forEach((k, v) -> copy.put(k, Map.copyOf(v)));
            multitexts = Map.copyOf(copy);
        }

        /**
         * The value of a scalar property, or {@code null}.
         *
         * @param name the property name
         * @return the value
         */
        public String scalar(String name) {
            return scalars.get(name);
        }
    }

    /**
     * Create a component under a parent.
     *
     * @param parent   the parent component
     * @param spec     the component type and its initializers
     * @param position the 1-based position among same-type siblings, or {@code null}
     *                 for the last position; ignored for a typed component
     * @return a handle on the new component
     * @throws UnsupportedByModelException if the dictionary model cannot hold it
     */
    public ComponentRef createChild(ComponentRef parent, NewComponent spec, Integer position) {
        ComponentRef created = switch (spec.componentType()) {
            case "entry" -> createEntry(spec);
            case "sense" -> createSense(parent, spec, position);
            case "example" -> createExample(parent, spec, position);
            case "etymology" -> createEtymology(parent, spec);
            case "variant" -> createVariant(parent, spec, position);
            case "relation" -> createRelation(parent, spec, position);
            case "pronunciation" -> createPronunciation(parent, spec, position);
            case "illustration" -> createIllustration(parent, spec, position);
            case "media" -> createMedia(parent, spec, position);
            case "reversal" -> createReversal(parent, spec, position);
            case "trait" -> createTrait(parent, spec, position);
            case "annotation" -> createAnnotation(parent, spec, position);
            case "note" -> createNote(parent, spec);
            case "field" -> createField(parent, spec);
            case "translation" -> createTranslation(parent, spec);
            default -> throw new UnsupportedByModelException(
                "the dictionary model cannot create a `" + spec.componentType() + "`");
        };
        applyMultitexts(created, spec);
        return created;
    }

    private void applyMultitexts(ComponentRef ref, NewComponent spec) {
        spec.multitexts().forEach((property, values) ->
            values.forEach((lang, text) -> setQualified(ref, property, lang, text)));
    }

    private ComponentRef createEntry(NewComponent spec) {
        LiftEntry entry = LiftEntry.create();
        dictionary.addEntry(entry);
        homophones.invalidate();
        return new ComponentRef.Node("entry", entry);
    }

    private ComponentRef createSense(ComponentRef parent, NewComponent spec, Integer position) {
        LiftSense sense = LiftSense.create();
        AbstractLiftRoot node = requireNode(parent);
        if (node instanceof LiftEntry entry) {
            insert(position, entry.getSenses(), i -> entry.addSense(i, sense));
        } else if (node instanceof LiftSense host) {
            insert(position, host.getSenses(), i -> host.addSense(i, sense));
        } else {
            throw unsupportedParent(parent, "sense");
        }
        return new ComponentRef.Node("sense", sense);
    }

    private ComponentRef createExample(ComponentRef parent, NewComponent spec, Integer position) {
        LiftSense sense = requireType(parent, LiftSense.class, "example");
        LiftExample example = LiftExample.create();
        insert(position, sense.getExamples(), i -> sense.addExample(i, example));
        return new ComponentRef.Node("example", example);
    }

    private ComponentRef createEtymology(ComponentRef parent, NewComponent spec) {
        LiftEntry entry = requireType(parent, LiftEntry.class, "etymology");
        Feature type = featureFor(header().getEtymologyTypeManager(), required(spec, "type", "etymology"));
        LiftEtymology etymology = LiftEtymology.create(type, spec.scalar("source"));
        entry.addEtymology(etymology);
        return new ComponentRef.Node("etymology", etymology);
    }

    private ComponentRef createVariant(ComponentRef parent, NewComponent spec, Integer position) {
        LiftEntry entry = requireType(parent, LiftEntry.class, "variant");
        LiftVariant variant = new LiftVariant();
        insert(position, entry.getVariants(), i -> entry.addVariant(i, variant));
        return new ComponentRef.Node("variant", variant);
    }

    private ComponentRef createRelation(ComponentRef parent, NewComponent spec, Integer position) {
        LiftRelation relation = LiftRelation.create(
            featureFor(header().getRelationTypeManager(), required(spec, "type", "relation")));
        setCreatedReference(spec.scalar("target"), relation::setRefId, relation::setRefObject);
        AbstractLiftRoot node = requireNode(parent);
        switch (node) {
            case LiftEntry entry -> insert(position, entry.getRelations(),
                i -> entry.addRelation(i, relation));
            case LiftSense sense -> insert(position, sense.getRelations(),
                i -> sense.addRelation(i, relation));
            case LiftVariant variant -> insert(position, variant.getRelations(),
                i -> variant.addRelation(i, relation));
            default -> throw unsupportedParent(parent, "relation");
        }
        return new ComponentRef.Node("relation", relation);
    }

    private ComponentRef createPronunciation(
        ComponentRef parent, NewComponent spec, Integer position
    ) {
        LiftPronunciation pronunciation = LiftPronunciation.create();
        AbstractLiftRoot node = requireNode(parent);
        switch (node) {
            case LiftEntry entry -> insert(position, entry.getPronunciations(),
                i -> entry.addPronunciation(i, pronunciation));
            case LiftVariant variant -> insert(position, variant.getPronunciations(),
                i -> variant.addPronunciation(i, pronunciation));
            default -> throw unsupportedParent(parent, "pronunciation");
        }
        return new ComponentRef.Node("pronunciation", pronunciation);
    }

    private ComponentRef createIllustration(
        ComponentRef parent, NewComponent spec, Integer position
    ) {
        LiftSense sense = requireType(parent, LiftSense.class, "illustration");
        LiftIllustration illustration =
            new LiftIllustration(required(spec, "url", "illustration"));
        insert(position, sense.getIllustrations(),
            i -> sense.addIllustration(i, illustration));
        return new ComponentRef.Node("illustration", illustration);
    }

    private ComponentRef createMedia(ComponentRef parent, NewComponent spec, Integer position) {
        LiftPronunciation pronunciation = requireType(parent, LiftPronunciation.class, "media");
        LiftMedia media = new LiftMedia(required(spec, "url", "media"));
        insert(position, pronunciation.getMedias(),
            i -> pronunciation.addMedia(i, media));
        return new ComponentRef.Node("media", media);
    }

    private ComponentRef createReversal(ComponentRef parent, NewComponent spec, Integer position) {
        LiftReversal reversal = new LiftReversal(
            featureFor(header().getInverseTypeManager(), required(spec, "type", "reversal")));
        AbstractLiftRoot node = requireNode(parent);
        switch (node) {
            case LiftSense sense -> insert(position, sense.getReversals(),
                i -> sense.addReversal(i, reversal));
            default -> throw unsupportedParent(parent, "reversal");
        }
        return new ComponentRef.Node("reversal", reversal);
    }

    private ComponentRef createTrait(ComponentRef parent, NewComponent spec, Integer position) {
        TraitSpecification definition = traitSpecification(required(spec, "type", "trait"));
        // The value is parsed after what the name refers to, as the dictionary model's
        // own reader and TraitBuilder do: a trait whose definition has an option range,
        // or that is named after a range, holds features of that range.
        LiftTrait trait = new LiftTrait(definition);
        trait.setTypedValue(traitValue(definition, spec.scalars().getOrDefault("value", "")));
        if (parent instanceof ComponentRef.Category category) {
            GrammaticalInfo info = grammaticalInfo(category.host()).orElseThrow(() ->
                new UnsupportedByModelException(
                    "the sense has no grammatical information to carry a trait; write its `value` "
                        + "first"));
            info.addTrait(trait);
            return new ComponentRef.Node("trait", trait);
        }
        AbstractLiftRoot node = requireNode(parent);
        if (node instanceof AbstractExtensibleWithoutField host) {
            insert(position, host.getTraits(), i -> host.addTrait(i, trait));
            return new ComponentRef.Node("trait", trait);
        }
        if (node instanceof GrammaticalInfo info) {
            info.addTrait(trait);
            return new ComponentRef.Node("trait", trait);
        }
        throw unsupportedParent(parent, "trait");
    }

    private ComponentRef createAnnotation(
        ComponentRef parent, NewComponent spec, Integer position
    ) {
        LiftAnnotation annotation = new LiftAnnotation(
            featureFor(header().getAnnotationTypeManager(), required(spec, "type", "annotation")));
        annotation.setValue(spec.scalar("value"));
        annotation.setWhen(spec.scalar("when"));
        annotation.setWho(spec.scalar("who"));
        AbstractLiftRoot node = requireNode(parent);
        if (node instanceof AbstractExtensibleWithoutField host) {
            insert(position, host.getAnnotations(),
                i -> host.addAnnotation(i, annotation));
            return new ComponentRef.Node("annotation", annotation);
        }
        if (node instanceof LiftTrait trait) {
            trait.addAnnotation(annotation);
            return new ComponentRef.Node("annotation", annotation);
        }
        throw unsupportedParent(parent, "annotation");
    }

    private ComponentRef createNote(ComponentRef parent, NewComponent spec) {
        AbstractNotable host = requireType(parent, AbstractNotable.class, "note");
        LiftNote note = LiftNote.create();
        note.setType(featureFor(header().getNoteTypeManager(), required(spec, "type", "note")));
        host.addNote(note);
        return new ComponentRef.Node("note", note);
    }

    private ComponentRef createField(ComponentRef parent, NewComponent spec) {
        AbstractExtensibleWithField host =
            requireType(parent, AbstractExtensibleWithField.class, "field");
        LiftField field = LiftField.create(fieldSpecification(required(spec, "type", "field")));
        host.addField(field);
        return new ComponentRef.Node("field", field);
    }

    /**
     * What the type of a new trait refers to: its definition, or the feature set it is
     * named after (whose features are its values). A name that refers to neither is
     * declared, with the default data model of a trait
     * (Part 1, section 2.1.5).
     */
    private TraitSpecification traitSpecification(String name) {
        TraitSpecification resolved = header().resolveTraitSpecification(name);
        if (resolved instanceof UndeclaredName) {
            return header().declareTraitDefinition(name);
        }
        return resolved;
    }

    /**
     * The definition of the type of a new field. A name that has none is declared,
     * with the default data model of a field (Part 1,
     * section 2.1.5).
     */
    private FieldSpecification fieldSpecification(String name) {
        FieldSpecification resolved = header().resolveFieldSpecification(name);
        if (resolved instanceof UndeclaredName) {
            return header().declareFieldDefinition(name);
        }
        return resolved;
    }

    private ComponentRef createTranslation(ComponentRef parent, NewComponent spec) {
        LiftExample example = requireType(parent, LiftExample.class, "translation");
        Feature key = featureFor(header().getTranslationTypeManager(), required(spec, "type", "translation"));
        LiftTranslation translation = LiftTranslation.create(key);
        example.addTranslation(translation);
        return new ComponentRef.Node("translation", translation);
    }

    private void setCreatedReference(
        String value,
        java.util.function.Consumer<String> idSetter,
        java.util.function.Consumer<AbstractIdentifiable> objectSetter
    ) {
        if (value == null) {
            return;
        }
        idSetter.accept(value);
        AbstractIdentifiable target =
            dictionary.getLiftDictionaryRegistry().getEntryOrSenseByLiftId(value);
        if (target != null) {
            objectSetter.accept(target);
        }
    }

    /**
     * Insert a component into an ordered sibling list at a 1-based position.
     *
     * <p>The component is added at its index through the model's own indexed
     * {@code addX(int, …)}, which is what registers it in the dictionary; the
     * sibling lists the model hands out are read-only views. A missing position,
     * or one past the end, appends.</p>
     *
     * @param position the 1-based position, or {@code null} for the last one
     * @param siblings the current same-type siblings, read only for their number
     * @param addAt    adds the component at a 0-based index
     */
    private void insert(Integer position, List<?> siblings, IntConsumer addAt) {
        addAt.accept(indexWithin(position == null ? -1 : position - 1, siblings));
    }

    /**
     * A 0-based index at which to add a component among {@code siblings}: the index
     * itself when it designates a position in the list or just after it, the end of
     * the list otherwise.
     */
    private static int indexWithin(int index, List<?> siblings) {
        return index >= 0 && index <= siblings.size() ? index : siblings.size();
    }

    /**
     * Delete a component with its descendants.
     *
     * @param ref the component to delete
     * @throws UnsupportedByModelException if the dictionary model cannot remove it
     */
    public void delete(ComponentRef ref) {
        if (!(ref instanceof ComponentRef.Node node)) {
            throw new UnsupportedByModelException(
                "a `" + ref.componentType() + "` is never removed by a command");
        }
        AbstractLiftRoot child = node.node();
        // The dictionary model refuses to remove a component that something still
        // refers to. The language, by contrast, does not maintain referential
        // integrity (Part 2, section 10): deleting a referenced component is legal,
        // and the reference is reported as dangling rather than repaired. The
        // inbound references are therefore unlinked, the component removed, and the
        // inbound references put back, still pointing at the component that is gone.
        // Unlinking and restoring the references changes nothing in the referring
        // entries, once the deletion is over: their changes are attributed to none.
        List<Runnable> restoreInbound = journal.unattributed(() -> detachInboundReferences(ref));

        if (child instanceof LiftEntry entry) {
                dictionary.removeEntry(entry);
            homophones.invalidate();

        } else {
            AbstractLiftRoot parent = child.getParentNode();
            int position = indexOfSibling(parent, child);
            deleteFromParent(parent, child);
        }
        journal.unattributed(() -> {
            restoreInbound.forEach(Runnable::run);
            return null;
        });
    }

    /**
     * Unlink every reference pointing into the subtree about to be removed, and
     * return the actions that put them back.
     */
    private List<Runnable> detachInboundReferences(ComponentRef ref) {
        Set<String> ids = new LinkedHashSet<>();
        collectIds(ref, ids);
        List<Runnable> restore = new ArrayList<>();
        for (String id : ids) {
            for (HasRefId referring
                : List.copyOf(dictionary.getLiftDictionaryRegistry().getReferencesTo(id))) {
                AbstractLiftRoot node = (AbstractLiftRoot) referring;
                AbstractLiftRoot parent = node.getParentNode();
                if (parent == null) {
                    continue;
                }
                int position = indexOfSibling(parent, node);
                deleteFromParent(parent, node);
                restore.add(() -> reattach(parent, node, position));
            }
        }
        return restore;
    }

    private void collectIds(ComponentRef ref, Set<String> ids) {
        idOf(ref).ifPresent(ids::add);
        for (String childType : metamodel.requireComponentType(ref.componentType()).children()) {
            List<ComponentRef> children;
            try {
                children = children(ref, childType);
            } catch (UnsupportedByModelException e) {
                continue;
            }
            for (ComponentRef child : children) {
                collectIds(child, ids);
            }
        }
    }

    private void deleteFromParent(AbstractLiftRoot parent, AbstractLiftRoot child) {
        switch (child) {
            case LiftSense s -> s.getParent().deleteSense(s);
            case LiftExample x -> x.getParent().deleteExample(x);
            case LiftEtymology y -> y.getParent().deleteEtymology(y);
            case LiftVariant v -> v.getParent().deleteVariant(v);
            case LiftRelation r -> r.getParent().deleteRelation(r);
            case LiftPronunciation p -> p.getParent().deletePronunciation(p);
            case LiftIllustration i -> i.getParent().deleteIllustration(i);
            case LiftMedia m -> m.getParent().deleteMedia(m);
            case LiftReversal l -> l.getParent().deleteReversal(l);
            case LiftNote n -> n.getParent().deleteNote(n);
            case LiftField f -> f.getParent().deleteField(f);
            case LiftTrait t -> t.getParent().deleteTrait(t);
            case LiftAnnotation a -> a.getParent().deleteAnnotation(a);
            case LiftTranslation t -> t.getParent().deleteTranslation(t);
            default -> throw new UnsupportedByModelException(
                "the dictionary model cannot remove a " + child.getClass().getSimpleName());
        }
    }

    /**
     * Put a component back under {@code parent}, at the 0-based {@code position}
     * among its same-type siblings when it is an ordered one; a position out of
     * range appends. Typed components have no position and are simply added.
     */
    private void reattach(AbstractLiftRoot parent, AbstractLiftRoot child, int position) {
        switch (child) {
            case LiftSense s -> {
                if (parent instanceof LiftEntry e) {
                    e.addSense(indexWithin(position, e.getSenses()), s);
                } else {
                    LiftSense host = (LiftSense) parent;
                    host.addSense(indexWithin(position, host.getSenses()), s);
                }
            }
            case LiftExample x -> {
                LiftSense sense = (LiftSense) parent;
                sense.addExample(indexWithin(position, sense.getExamples()), x);
            }
            case LiftEtymology y -> {
                LiftEntry entry = (LiftEntry) parent;
                entry.addEtymology(indexWithin(position, entry.getEtymologies()), y);
            }
            case LiftVariant v -> {
                LiftEntry entry = (LiftEntry) parent;
                entry.addVariant(indexWithin(position, entry.getVariants()), v);
            }
            case LiftRelation r -> {
                switch (parent) {
                    case LiftEntry e -> e.addRelation(indexWithin(position, e.getRelations()), r);
                    case LiftSense s -> s.addRelation(indexWithin(position, s.getRelations()), r);
                    default -> {
                        LiftVariant v = (LiftVariant) parent;
                        v.addRelation(indexWithin(position, v.getRelations()), r);
                    }
                }
            }
            case LiftPronunciation p -> {
                if (parent instanceof LiftEntry e) {
                    e.addPronunciation(indexWithin(position, e.getPronunciations()), p);
                } else {
                    LiftVariant v = (LiftVariant) parent;
                    v.addPronunciation(indexWithin(position, v.getPronunciations()), p);
                }
            }
            case LiftIllustration i -> {
                LiftSense sense = (LiftSense) parent;
                sense.addIllustration(indexWithin(position, sense.getIllustrations()), i);
            }
            case LiftMedia m -> {
                LiftPronunciation pronunciation = (LiftPronunciation) parent;
                pronunciation.addMedia(indexWithin(position, pronunciation.getMedias()), m);
            }
            case LiftReversal l -> {
                fr.cnrs.lacito.liftapi.model.HasReversal host =
                    (fr.cnrs.lacito.liftapi.model.HasReversal) parent;
                host.addReversal(indexWithin(position, host.getReversals()), l);
            }
            case LiftNote n -> ((AbstractNotable) parent).addNote(n);
            case LiftField f -> ((AbstractExtensibleWithField) parent).addField(f);
            case LiftTrait t -> {
                fr.cnrs.lacito.liftapi.model.HasTrait host =
                    (fr.cnrs.lacito.liftapi.model.HasTrait) parent;
                host.addTrait(indexWithin(position, host.getTraits()), t);
            }
            case LiftAnnotation a -> {
                fr.cnrs.lacito.liftapi.model.HasAnnotation host =
                    (fr.cnrs.lacito.liftapi.model.HasAnnotation) parent;
                host.addAnnotation(indexWithin(position, host.getAnnotations()), a);
            }
            case LiftTranslation t -> ((LiftExample) parent).addTranslation(t);
            default -> throw new UnsupportedByModelException(
                "the dictionary model cannot restore a " + child.getClass().getSimpleName());
        }
    }

    private int indexOfSibling(AbstractLiftRoot parent, AbstractLiftRoot child) {
        if (parent == null) {
            return -1;
        }
        List<ComponentRef> siblings =
            children(new ComponentRef.Node(typeOf(parent), parent), typeOf(child));
        return siblings.indexOf(new ComponentRef.Node(typeOf(child), child));
    }

    /**
     * Move an ordered component, changing its position, its parent, or both.
     *
     * @param ref       the component to move
     * @param newParent the destination parent
     * @param position  the 1-based destination position among same-type siblings
     */
    public void move(ComponentRef ref, ComponentRef newParent, int position) {
        AbstractLiftRoot child = ((ComponentRef.Node) ref).node();
        // `detach` unlinks without unregistering, which is the model's documented
        // way of moving a subtree inside one dictionary.
        child.detach();
        reattach(requireNode(newParent), child, position - 1);

    }

    // ===================================================================
    // Small helpers
    // ===================================================================

    private static AbstractLiftRoot requireNode(ComponentRef ref) {
        if (ref instanceof ComponentRef.Node node) {
            return node.node();
        }
        throw new UnsupportedByModelException(
            "a `" + ref.componentType() + "` cannot be the parent of a component here");
    }

    private static <T> T requireType(ComponentRef ref, Class<T> expected, String childType) {
        AbstractLiftRoot node = requireNode(ref);
        if (expected.isInstance(node)) {
            return expected.cast(node);
        }
        throw unsupportedParent(ref, childType);
    }

    private static String required(NewComponent spec, String property, String componentType) {
        String value = spec.scalar(property);
        if (value == null) {
            throw new UnsupportedByModelException(
                "a `" + componentType + "` needs its `" + property + "` at creation");
        }
        return value;
    }

    private static UnsupportedByModelException unsupportedParent(
        ComponentRef parent, String childType
    ) {
        return new UnsupportedByModelException(
            "the dictionary model holds no `" + childType + "` under a `"
                + parent.componentType() + "`");
    }

    private static UnsupportedByModelException unsupportedProperty(
        String componentType, String property
    ) {
        return new UnsupportedByModelException(
            "the dictionary model has no field for `" + componentType + "." + property + "`");
    }
}
