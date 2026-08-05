

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reflection-based data binding between JavaFX controllers/UI components and POJO models.
 * Automates synchronisation for text inputs, checkboxes, combo boxes, spinners,
 * date pickers, sliders, lists, and hierarchical structures.
 *
 * <p>Supported UI components:
 * <ul>
 *   <li>{@link TextInputControl} — TextField, TextArea, PasswordField</li>
 *   <li>{@link Labeled} — Label and other read-only text displays</li>
 *   <li>{@link CheckBox}, {@link RadioButton}, {@link ToggleButton} — boolean fields</li>
 *   <li>{@link ComboBox} — any object field</li>
 *   <li>{@link ChoiceBox} — any object field</li>
 *   <li>{@link Spinner} — numeric fields</li>
 *   <li>{@link DatePicker} — {@link LocalDate} fields</li>
 *   <li>{@link Slider} — double/float/int fields</li>
 *   <li>{@link TableView} — {@code List<T>} fields (auto or manual columns)</li>
 *   <li>{@link ListView} — {@code List<T>} fields; selection triggers recursive rebind</li>
 *   <li>{@link TreeView} — recursive {@code Collection<T>} where {@code T} is the declaring
 *       class itself; selection triggers recursive rebind of the selected node's scalar fields</li>
 * </ul>
 *
 * <p>Custom lowercase prefixes on controller fields are supported (e.g. {@code btn}, {@code lbl}, {@code txt}).
 */
public class PojoModelBinder {

    private static final Logger LOG = Logger.getLogger(PojoModelBinder.class.getName());
    private static final Map<Class<?>, Map<String, List<Field>>> controllerFieldsMap = new IdentityHashMap<>();

    private static final String UNSUBSCRIBE_KEY = "pojoModelBinder.unsubscribe";
    private static final String SEL_UNSUBSCRIBE_KEY = "pojoModelBinder.selUnsubscribe";

    /**
     * Binds all fields of {@code model} to matching UI components in {@code controller}.
     * Safe to call multiple times — old listeners are replaced, not stacked.
     *
     * @param controller controller instance containing JavaFX UI component fields
     * @param model      model instance to synchronise with the UI
     */
    public static void bind(Object controller, Object model) {
        for (Class<?> c = model.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.trySetAccessible()) {
                    try {
                        bindModelField(controller, f, model);
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Failed to bind: " + f.getName(), e);
                    }
                }
            }
        }
    }

    /**
     * Removes all listeners registered by {@link #bind} for every UI component in the controller.
     * Call when the controller is being destroyed to avoid memory leaks.
     *
     * @param controller controller whose bindings should be released
     */
    @SuppressWarnings("unused")
    public static void unbind(Object controller) {
        for (Class<?> c = controller.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                try {
                    if (f.trySetAccessible()) {
                        removeListeners(f.get(controller));
                    } else {
                        throw new IllegalAccessException("Failed to unbind: " + f.getName());
                    }
                } catch (Exception e) {
                    LOG.log(Level.FINE, "unbind: " + f.getName(), e);
                }

            }
        }
    }

    private static void bindModelField(Object controller, Field mf, Object model) throws Exception {
        Class<?> type = mf.getType();
        if (isSimpleType(type)) bindSimpleField(controller, mf, model);
        else if (isRecursiveCollection(mf, model.getClass())) bindTreeField(controller, mf, model);
        else if (Collection.class.isAssignableFrom(type)) bindListField(controller, mf, model);
        else bindNestedObject(controller, mf, model);
    }

    /**
     * Returns true when the field is a {@code Collection<T>} whose element type equals
     * {@code ownerClass} — the canonical marker for a self-referential tree structure.
     */
    private static boolean isRecursiveCollection(Field mf, Class<?> ownerClass) {
        if (!Collection.class.isAssignableFrom(mf.getType())) return false;
        return ownerClass.equals(resolveCollectionGenericType(mf));
    }

    // -------------------------------------------------------------------------
    // Simple (scalar) fields
    // -------------------------------------------------------------------------

    private static void bindSimpleField(Object controller, Field mf, Object model) throws Exception {
        for (Field uf : findUiFields(controller, mf.getName())) {
            if (!uf.trySetAccessible()) continue;
            Object ui = uf.get(controller);
            if (ui == null) continue;
            switch (ui) {
                case TextInputControl tic -> bindControl(tic, tic.textProperty(),
                        PropertyConverter.toUiString(mf.get(model)),
                        tic::setText,
                        (v) -> mf.set(model, PropertyConverter.toModel(v, mf.getType())));

                case CheckBox cb -> bindControl(cb, cb.selectedProperty(),
                        toBooleanValue(mf.get(model)),
                        cb::setSelected,
                        (v) -> mf.set(model, v));

                case ToggleButton tb -> bindControl(tb, tb.selectedProperty(),
                        toBooleanValue(mf.get(model)),
                        tb::setSelected,
                        (v) -> mf.set(model, v));

                case ComboBox<?> combo -> {
                    @SuppressWarnings("unchecked") ComboBox<Object> cb = (ComboBox<Object>) combo;
                    bindControl(cb, cb.valueProperty(),
                            mf.get(model),
                            cb::setValue,
                            (v) -> mf.set(model, v));
                }

                case ChoiceBox<?> choice -> {
                    @SuppressWarnings("unchecked") ChoiceBox<Object> cb = (ChoiceBox<Object>) choice;
                    bindControl(cb, cb.valueProperty(),
                            mf.get(model),
                            cb::setValue,
                            (v) -> mf.set(model, v));
                }

                case Spinner<?> sp -> {
                    @SuppressWarnings("unchecked") Spinner<Object> s = (Spinner<Object>) sp;
                    Object cur = mf.get(model);
                    if (cur != null && s.getValueFactory() != null) s.getValueFactory().setValue(cur);
                    bindControl(s, s.valueProperty(),
                            cur,
                            (v) -> {
                                if (s.getValueFactory() != null) s.getValueFactory().setValue(v);
                            },
                            (v) -> {
                                if (v != null) mf.set(model, PropertyConverter.toModel(v.toString(), mf.getType()));
                            });
                }

                case DatePicker dp -> {
                    Object v = mf.get(model);
                    bindControl(dp, dp.valueProperty(),
                            v instanceof LocalDate ld ? ld : null,
                            dp::setValue,
                            (d) -> mf.set(model, d));
                }

                case Slider sl -> {
                    Object v = mf.get(model);
                    double init = v instanceof Number n ? n.doubleValue() : 0.0;
                    bindControl(sl, sl.valueProperty(),
                            init,
                            (val) -> sl.setValue(val.doubleValue()),
                            (val) -> mf.set(model, PropertyConverter.toModel(val.toString(), mf.getType())));
                }

                case Labeled lbl -> lbl.setText(PropertyConverter.toUiString(mf.get(model)));

                default -> LOG.warning("Unsupported UI component " + ui.getClass().getSimpleName()
                                       + " for field: " + mf.getName());
            }
        }
    }

    private static <T> void bindControl(Node node,
                                        ObservableValue<T> property,
                                        T initialValue,
                                        ModelSetter<T> uiSetter,
                                        ModelSetter<T> modelSetter) {
        removeListeners(node);
        try {
            uiSetter.set(initialValue);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Initial value set failed", e);
        }

        ChangeListener<T> listener = (obs, o, n) -> {
            try {
                modelSetter.set(n);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Listener update failed", e);
            }
        };

        node.getProperties().put(UNSUBSCRIBE_KEY, (Runnable) () -> property.removeListener(listener));
        property.addListener(listener);
    }

    @FunctionalInterface
    private interface ModelSetter<T> {
        void set(T value) throws Exception;
    }


    // -------------------------------------------------------------------------
    // List / Collection fields (TableView / ListView / ComboBox / ChoiceBox)
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static void bindListField(Object controller, Field mf, Object model) throws Exception {
        for (Field uf : findUiFields(controller, mf.getName())) {
            if (!uf.trySetAccessible()) continue;
            Object ui = uf.get(controller);
            if (ui == null) continue;

            Collection<Object> rawCollection = (Collection<Object>) mf.get(model);
            List<Object> list = rawCollection instanceof List
                    ? (List<Object>) rawCollection
                    : (rawCollection != null ? new ArrayList<>(rawCollection) : null);
            if (list == null) {
                list = FXCollections.observableArrayList();
                mf.set(model, list);
            }
            ObservableList<Object> obs = list instanceof ObservableList
                    ? (ObservableList<Object>) list
                    : FXCollections.observableArrayList(list);
            if (!(list instanceof ObservableList)) mf.set(model, obs);

            switch (ui) {
                case TableView<?> tv -> {
                    TableView<Object> tableView = (TableView<Object>) tv;
                    tableView.setItems(obs);
                    if (tableView.getColumns().isEmpty()) autoGenerateColumns(tableView, mf);
                    else bindExistingColumns(tableView, mf);
                }
                case ListView<?> lv -> {
                    ListView<Object> listView = (ListView<Object>) lv;
                    listView.setItems(obs);
                    attachSelectionRebind(listView.getSelectionModel().selectedItemProperty(), controller, mf, listView);
                }
                case ComboBox<?> combo -> {
                    ComboBox<Object> cb = (ComboBox<Object>) combo;
                    cb.setItems(obs);
                    attachSelectionRebind(cb.getSelectionModel().selectedItemProperty(), controller, mf, cb);
                }
                case ChoiceBox<?> choice -> {
                    ChoiceBox<Object> cb = (ChoiceBox<Object>) choice;
                    cb.setItems(obs);
                    attachSelectionRebind(cb.getSelectionModel().selectedItemProperty(), controller, mf, cb);
                }
                default -> LOG.warning("Unsupported UI component " + ui.getClass().getSimpleName()
                                       + " for field: " + mf.getName());
            }
        }
    }



    // -------------------------------------------------------------------------
    // Tree fields (TreeView) — triggered by recursive Collection<T extends owner>
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static void bindTreeField(Object controller, Field mf, Object model) throws Exception {
        for (Field uf : findUiFields(controller, mf.getName())) {
            if (!uf.trySetAccessible()) continue;
            Object ui = uf.get(controller);
            if (!(ui instanceof TreeView<?> tv)) {
                LOG.warning("Expected TreeView for recursive field '" + mf.getName()
                            + "' but got: " + (ui == null ? "null" : ui.getClass().getSimpleName()));
                continue;
            }
            TreeView<Object> treeView = (TreeView<Object>) tv;

            // Build root item from the model itself (the root node IS the model)
            TreeItem<Object> root = buildTreeItem(model, mf);
            root.setExpanded(true);
            if (treeView.getRoot()==null) treeView.setRoot(root);
            attachTreeSelectionRebind(treeView, controller);
        }
    }

    /**
     * Recursively builds a {@link TreeItem} tree from {@code node} by following
     * the self-referential {@code childrenField}.
     *
     * @param node          the current model node
     * @param childrenField the {@code Collection<T>} field that holds child nodes
     */
    @SuppressWarnings("unchecked")
    private static TreeItem<Object> buildTreeItem(Object node, Field childrenField) throws Exception {
        TreeItem<Object> item = new TreeItem<>(node);
        Collection<Object> children = (Collection<Object>) childrenField.get(node);
        if (children != null) {
            for (Object child : children) {
                item.getChildren().add(buildTreeItem(child, childrenField));
            }
        }
        return item;
    }

    /**
     * Attaches a selection listener to {@code treeView}: when the user selects a node,
     * its scalar fields are rebound to matching UI components in the controller —
     * identical behaviour to {@link #attachSelectionRebind} for ListView.
     */
    private static void attachTreeSelectionRebind(TreeView<Object> treeView, Object controller) {
        ObservableValue<TreeItem<Object>> selectedProp;
        selectedProp = treeView.getSelectionModel().selectedItemProperty();

        Object old = treeView.getProperties().get(SEL_UNSUBSCRIBE_KEY);
        if (old instanceof Runnable r) r.run();

        ChangeListener<TreeItem<Object>> listener = (obs, prev, next) -> {
            if (next != null && next.getValue() != null ) bind(controller, next.getValue());
        };
        treeView.getProperties().put(SEL_UNSUBSCRIBE_KEY,
                (Runnable) () -> selectedProp.removeListener(listener));
        selectedProp.addListener(listener);
    }

    private static void attachSelectionRebind(ObservableValue<Object> selectedProp,
                                              Object controller,
                                              Field mf,
                                              Node node) {
        Class<?> itemType = resolveCollectionGenericType(mf);
        if (itemType == null || isSimpleType(itemType)) return;  //skip

        Object old = node.getProperties().get(SEL_UNSUBSCRIBE_KEY);
        if (old instanceof Runnable r) r.run();

        ChangeListener<Object> listener = (obs, o, n) -> {
            if (n != null) bind(controller, n);
        };
        node.getProperties().put(SEL_UNSUBSCRIBE_KEY,
                (Runnable) () -> selectedProp.removeListener(listener));
        selectedProp.addListener(listener);
    }

    // -------------------------------------------------------------------------
    // Nested POJO
    // -------------------------------------------------------------------------

    private static void bindNestedObject(Object controller, Field mf, Object model) throws Exception {
        Object value = mf.get(model);
        if (value == null) return;
        for (Field uf : findUiFields(controller, mf.getName())) {
            if (!uf.trySetAccessible()) continue;
            Object sub = uf.get(controller);
            if (sub != null) bind(sub, value);
        }
    }

    // -------------------------------------------------------------------------
    // TableView helpers
    // -------------------------------------------------------------------------

    private static void autoGenerateColumns(TableView<Object> tv, Field mf) {
        Class<?> itemType = resolveCollectionGenericType(mf);
        if (itemType == null) return;
        tv.setEditable(true);
        for (Field f : itemType.getDeclaredFields()) {
            if (!f.trySetAccessible()) continue;
            TableColumn<Object, String> col = new TableColumn<>(f.getName());
            configureColumn(col, f);
            tv.getColumns().add(col);
        }
    }

    private static void bindExistingColumns(TableView<Object> tv, Field mf) {
        Class<?> itemType = resolveCollectionGenericType(mf);
        if (itemType == null) return;
        Map<String, Field> byName = new HashMap<>();
        for (Field f : itemType.getDeclaredFields()) {
            if (!f.trySetAccessible()) continue;
            byName.put(f.getName().toLowerCase(), f);
        }
        tv.setEditable(true);
        for (TableColumn<Object, ?> col : tv.getColumns()) {
            String id = col.getId();
            if (id == null) continue;
            byName.entrySet().stream()
                    .filter(e -> id.toLowerCase().endsWith(e.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .ifPresent(f -> configureStringColumn(col, f));
        }
    }

    @SuppressWarnings("unchecked") // all columns rendered as String via PropertyConverter.toUiString
    private static void configureStringColumn(TableColumn<Object, ?> col, Field f) {
        configureColumn((TableColumn<Object, String>) col, f);
    }

    private static void configureColumn(TableColumn<Object, String> col, Field f) {
        col.setCellValueFactory(cd -> {
            try {
                return new SimpleStringProperty(PropertyConverter.toUiString(f.get(cd.getValue())));
            } catch (IllegalAccessException e) {
                return new SimpleStringProperty("");
            }
        });
        col.setCellFactory(TextFieldTableCell.forTableColumn());
        col.setEditable(true);
        col.setOnEditCommit(ev -> {
            try {
                f.set(ev.getRowValue(), PropertyConverter.toModel(ev.getNewValue(), f.getType()));
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Cell edit failed: " + f.getName(), e);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Listener lifecycle
    // -------------------------------------------------------------------------

    private static void removeListeners(Object component) {
        if (!(component instanceof Node node)) return;
        Object unsub = node.getProperties().remove(UNSUBSCRIBE_KEY);
        if (unsub instanceof Runnable r) r.run();
        // SEL_UNSUBSCRIBE_KEY removed on rebind (attachSelectionRebind / attachTreeSelectionRebind)
    }

    // -------------------------------------------------------------------------
    // Reflection helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves the first generic type argument of any {@link Collection} field.
     * Replaces the old {@code resolveListGenericType} — works for {@code List}, {@code Set},
     * {@code Queue} and any other {@code Collection} subtype.
     */
    private static Class<?> resolveCollectionGenericType(Field field) {
        if (!(field.getGenericType() instanceof ParameterizedType pt)) return null;
        Type[] args = pt.getActualTypeArguments();
        if (args.length == 0) return null;
        if (args[0] instanceof Class<?> cls) return cls;
        if (args[0] instanceof WildcardType wt && wt.getUpperBounds().length > 0
            && wt.getUpperBounds()[0] instanceof Class<?> cls) return cls;
        return null;
    }

    private static List<Field> findUiFields(Object controller, String name) {
        return controllerFieldsMap
                .computeIfAbsent(controller.getClass(), k -> new IdentityHashMap<>())
                .computeIfAbsent(name, k -> collectUiFields(controller.getClass(), name));
    }

    private static List<Field> collectUiFields(Class<?> controllerClass, String name) {
        List<Field> result = new ArrayList<>();
        Field direct = findFieldInHierarchy(controllerClass, name);
        if (direct != null) {
            LOG.info("Binding '" + name + "' -> " + direct.getName());
            result.add(direct);
        }
        for (Class<?> c = controllerClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (result.contains(f)) continue; // added as direct
                if (stripPrefix(f.getName()).equals(name)) {
                    LOG.info("Binding '" + name + "' -> " + f.getName() + " (without prefix)");
                    result.add(f);
                } else if (stripSuffix(f).equals(name)) {
                    LOG.info("Binding '" + name + "' -> " + f.getName() + " (without suffix)");
                    result.add(f);
                }
            }
        }
        if (result.isEmpty()) LOG.warning("No UI field found for '" + name + "'");
        return result;
    }

    private static Field findFieldInHierarchy(Class<?> clazz, String name) {
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    private static String stripPrefix(String id) {
        for (int i = 0; i < id.length(); i++)
            if (Character.isUpperCase(id.charAt(i)))
                return Character.toLowerCase(id.charAt(i)) + id.substring(i + 1);
        return id;
    }

    private static String stripSuffix(Field f) {
        String name = f.getName(), suffix = f.getType().getSimpleName();
        return name.endsWith(suffix) ? name.substring(0, name.length() - suffix.length()) : name;
    }

    private static boolean isSimpleType(Class<?> c) {
        return c == String.class || PropertyConverter.isSupportedType(c);
    }

    private static boolean toBooleanValue(Object v) {
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s);
        return false;
    }

    // -------------------------------------------------------------------------
    // PropertyConverter
    // -------------------------------------------------------------------------

    private static final class PropertyConverter {

        private static final Map<Class<?>, Function<String, Object>> TO_MODEL = new HashMap<>();

        static {
            TO_MODEL.put(int.class, Integer::parseInt);
            TO_MODEL.put(long.class, Long::parseLong);
            TO_MODEL.put(double.class, Double::parseDouble);
            TO_MODEL.put(float.class, Float::parseFloat);
            TO_MODEL.put(byte.class, Byte::parseByte);
            TO_MODEL.put(short.class, Short::parseShort);
            TO_MODEL.put(char.class, s -> s.charAt(0));
            TO_MODEL.put(boolean.class, Boolean::parseBoolean);
            TO_MODEL.put(Integer.class, Integer::parseInt);
            TO_MODEL.put(Long.class, Long::parseLong);
            TO_MODEL.put(Double.class, Double::parseDouble);
            TO_MODEL.put(Float.class, Float::parseFloat);
            TO_MODEL.put(Byte.class, Byte::parseByte);
            TO_MODEL.put(Short.class, Short::parseShort);
            TO_MODEL.put(Character.class, s -> s.charAt(0));
            TO_MODEL.put(Boolean.class, Boolean::parseBoolean);
            TO_MODEL.put(BigInteger.class, BigInteger::new);
            TO_MODEL.put(BigDecimal.class, BigDecimal::new);
            TO_MODEL.put(LocalDate.class, LocalDate::parse);
            TO_MODEL.put(LocalTime.class, LocalTime::parse);
            TO_MODEL.put(LocalDateTime.class, LocalDateTime::parse);
            TO_MODEL.put(Instant.class, Instant::parse);
            TO_MODEL.put(ZonedDateTime.class, ZonedDateTime::parse);
            TO_MODEL.put(OffsetDateTime.class, OffsetDateTime::parse);
            TO_MODEL.put(OffsetTime.class, OffsetTime::parse);
            TO_MODEL.put(Duration.class, Duration::parse);
            TO_MODEL.put(Period.class, Period::parse);
        }

        static Object toModel(String value, Class<?> target) {
            if (target == String.class) return value;
            Function<String, Object> c = TO_MODEL.get(target);
            if (c == null) throw new UnsupportedOperationException("No converter for: " + target.getName());
            return c.apply(value);
        }

        static String toUiString(Object v) {
            return v == null ? "" : v.toString();
        }

        static boolean isSupportedType(Class<?> c) {
            return TO_MODEL.containsKey(c);
        }

        private PropertyConverter() {
        }
    }
}
