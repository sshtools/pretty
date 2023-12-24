package com.sshtools.pretty;

import static com.sshtools.jajafx.FXUtil.maybeQueue;
import static com.sshtools.pretty.Resources.messageOrDefault;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.jini.Data;
import com.sshtools.jini.Data.Handle;
import com.sshtools.jini.Data.ValueUpdateEvent;
import com.sshtools.jini.INI;
import com.sshtools.jini.INI.Section;
import com.sshtools.jini.INIReader;
import com.sshtools.jini.INIReader.Builder;
import com.sshtools.jini.INIReader.MultiValueMode;
import com.sshtools.jini.INIWriter;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.FloatProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

public final class Configuration {
	static Logger LOG = LoggerFactory.getLogger(Configuration.class);
	
	public enum Change {
		ADDED, CHANGED, DEFAULT
	}
	
	public enum TriState {
		ON, OFF, AUTO
	}

	public record ValueMeta(String name, String description, String[] defaultValue, String[] value,
			Change change) {
	}

	public record SectionMeta(String[] path, String description, List<SectionMeta> sections, List<ValueMeta> values) {
		public ValueMeta value(String key) {
			return valueOr(key).orElseThrow(() -> new IllegalArgumentException(MessageFormat.format("No value named {0}", key)));
		}
		
		public Optional<ValueMeta> valueOr(String key) {
			return values.stream().filter(v -> v.name.equals(key)).findFirst();
		}
	}
	
	
	private record ObjectRef(ObjectProperty<?> prop, Class<?> clazz) {}

	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Configuration.class.getName());

	private Path configuration;
	private INI ini = INI.create();
	private Handle onValueUpdate, onSectionUpdate;
	private INI defaultConfiguration;
	private Map<String, List<ObjectRef>> objectProperties = new HashMap<>();
	private Map<String, List<StringProperty>> stringProperties = new HashMap<>();
	private Map<String, List<IntegerProperty>> integerProperties = new HashMap<>();
	private Map<String, List<FloatProperty>> floatProperties = new HashMap<>();
	private Map<String, List<BooleanProperty>> booleanProperties = new HashMap<>();

	public Configuration(Path dir) {
		try (var in = Configuration.class.getResourceAsStream("Configuration.ini")) {
			defaultConfiguration = configuredBuilder(new INIReader.Builder()).build().read(new InputStreamReader(in)).readOnly();
		} catch (IOException ioe) {
			throw new UncheckedIOException(ioe);
		} catch (ParseException e) {
			throw new IllegalStateException(e);
		}
		configuration = dir.resolve("pretty.ini");
		load();
	}

	public SectionMeta create(String... path) {
		var sectionMeta = sectionMeta(ini.create(path));
		save();
		return sectionMeta;
	}

	public String get(String key, String... path) {
		return resolve(ini, path).getOr(key).orElseGet(() -> resolve(defaultConfiguration, path).getOr(key).orElseThrow(
				() -> new IllegalArgumentException(MessageFormat.format("No configuration with key ''{0}''", key))));
	}

	public int getInt(String key, String... path) {
		return resolve(ini, path).getIntOr(key).orElseGet(() -> resolve(defaultConfiguration, path).getIntOr(key).orElseThrow(
				() -> new IllegalArgumentException(MessageFormat.format("No configuration with key ''{0}''", key))));
	}

	public float getFloat(String key, String... path) {
		return resolve(ini, path).getFloatOr(key).orElseGet(() -> resolve(defaultConfiguration, path).getFloatOr(key).orElseThrow(
				() -> new IllegalArgumentException(MessageFormat.format("No configuration with key ''{0}''", key))));
	}
	
	public String[] getAll(String key, String... path) {
		return resolve(ini, path).getAllOr(key).orElseGet(
				() -> resolve(defaultConfiguration, path).getAllOr(key).orElseThrow(() -> new IllegalArgumentException(
						MessageFormat.format("No configuration with key ''{0}''", key))));
	}
	
	public boolean getBoolean(String key, String... path) {
		return resolve(ini, path).getBooleanOr(key)
				.orElseGet(() -> resolve(defaultConfiguration, path).getBooleanOr(key)
						.orElseThrow(() -> new IllegalArgumentException(
								MessageFormat.format("No configuration with key ''{0}''", key))));
	}

	public StringProperty getStringProperty(String key, String... path) {
		var lst = resolveStringProperties(key, path);
		var sp = new SimpleStringProperty(get(key, path)) {
			@Override
			public void unbind() {
				lst.remove(this);
				super.unbind();
			}

			@Override
			public void removeListener(ChangeListener<? super String> listener) {
				lst.remove(this);
				super.removeListener(listener);
			}
		};
		sp.addListener((c,o,n) -> {
			put(key, n, path);
		});
		lst.add(sp);
		return sp;
	}

	public ObjectProperty<String[]> getStringsProperty(String key, String... path) {
		var lst = resolveObjectProperties(key, path);
		var strs = getAll(key, path);
		var sp = new SimpleObjectProperty<String[]>(strs) {
			@Override
			public void unbind() {
				lst.remove(new ObjectRef(this, String[].class));
				super.unbind();
			}
		};
		sp.addListener((c,o,n) -> {
			put(key, n, path);
		});
		lst.add(new ObjectRef(sp, String[].class));
		return sp;
	}

	public <E extends Enum<E>> ObjectProperty<E> getEnumProperty(Class<E> clazz, String key, String... path) {
		var lst = resolveObjectProperties(key, path);
		var sp = new SimpleObjectProperty<E>(Enum.valueOf(clazz, get(key, path))) {
			@Override
			public void unbind() {
				lst.remove(new ObjectRef(this, clazz));
				super.unbind();
			}
		};
		var ref = new ObjectRef(sp, clazz);
		sp.addListener((c,o,n) -> {
			put(key, n.name(), path);
		});
		lst.add(ref);
		return sp;
	}

	public BooleanProperty getBooleanProperty(String key, String... path) {
		var lst = resolveBooleanProperties(key, path);
		var sp = new SimpleBooleanProperty(getBoolean(key, path)) {
			@Override
			public void unbind() {
				lst.remove(this);
				super.unbind();
			}

			@Override
			public void bind(ObservableValue<? extends Boolean> rawObservable) {
				super.bind(rawObservable);
				lst.add(this);
			}
		};
		sp.addListener((c,o,n) -> {
			put(key, n, path);
		});
		lst.add(sp);
		return sp;
	}
	
	public FloatProperty getFloatProperty(String key, String... path) {
		var lst = resolveFloatProperties(key, path);
		var sp = new SimpleFloatProperty(getFloat(key, path)) {
			@Override
			public void unbind() {
				lst.remove(this);
				super.unbind();
			}

			@Override
			public void removeListener(ChangeListener<? super Number> listener) {
				lst.remove(this);
				super.removeListener(listener);
			}
		};
		sp.addListener((c,o,n) -> {
			put(key, n.floatValue(), path);
		});
		lst.add(sp);
		return sp;
	}	

	public IntegerProperty getIntProperty(String key, String... path) {
		var lst = resolveIntegerProperties(key, path);
		var sp = new SimpleIntegerProperty(getInt(key, path)) {
			@Override
			public void unbind() {
				lst.remove(this);
				super.unbind();
			}

			@Override
			public void removeListener(ChangeListener<? super Number> listener) {
				lst.remove(this);
				super.removeListener(listener);
			}
		};
		sp.addListener((c,o,n) -> {
			put(key, n.intValue(), path);
		});
		lst.add(sp);
		return sp;
	}
	
	public void load() {
		if (onValueUpdate != null) {
			onValueUpdate.close();
			onSectionUpdate.close();
			onValueUpdate = null;
			onSectionUpdate = null;
		}
		if (Files.exists(configuration)) {
			try {
				ini = configuredBuilder(new  INIReader.Builder()).build().read(configuration);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			} catch (ParseException e) {
				throw new IllegalStateException(e);
			}
			onValueUpdate = ini.onValueUpdate(evt -> {
				maybeQueue(() -> {
					var key = toKey(evt);
					var slst = stringProperties.get(key);
					if (slst != null) {
						slst.forEach(prop -> prop.set(evt.newValues().length == 0 ? "" : evt.newValues()[0]));
					}
					var ilst = integerProperties.get(key);
					if (ilst != null) {
						ilst.forEach(prop -> prop
								.set(evt.newValues().length == 0 ? 0 : Integer.parseInt(evt.newValues()[0])));
					}
					var flst = floatProperties.get(key);
					if (flst != null) {
						flst.forEach(prop -> prop
								.set(evt.newValues().length == 0 ? 0 : Float.parseFloat(evt.newValues()[0])));
					}
					var blst = booleanProperties.get(key);
					if (blst != null) {
						blst.forEach(prop -> prop
								.set(evt.newValues().length == 0 ? false : Boolean.parseBoolean(evt.newValues()[0])));
					}
					var olst = objectProperties.get(key);
					if (olst != null) {
						olst.forEach(prop -> objectValueUpdate(evt, prop));
					}
				});
			});
			onSectionUpdate = ini.onSectionUpdate(evt -> {
				// TODO
			});
		}
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void objectValueUpdate(ValueUpdateEvent evt, ObjectRef ref) {
		if(Enum.class.isAssignableFrom(ref.clazz())) {
			enumUpdate((ObjectProperty<Enum>)ref.prop(), (Class<Enum>)ref.clazz(), evt.newValues());
		}
		else if(ref.clazz().equals(String[].class)) {
			((ObjectProperty<String[]>)ref.prop()).setValue(evt.newValues());
		} else {
			throw new UnsupportedOperationException("Unknown object type.");
		}
	}
	
	private <E extends Enum<E>> void enumUpdate(ObjectProperty<E> prop, Class<E> clazz, String[] vals) {
		if(vals == null || vals.length < 1) {
			prop.set(clazz.getEnumConstants()[0]);
		}
		else {
			prop.set(Enum.valueOf(clazz, vals[0]));
		}
	}
	
	public Handle bindInteger(Consumer<Integer> setter, Supplier<Integer> getter, String key, String... path) {
		var prop = getIntProperty(key, path);
		ChangeListener<? super Number> lsnr = (c,o,n) -> {
			if(!getter.get().equals(n)) {
				setter.accept((Integer)n);	
			}
		};
		lsnr.changed(prop, null, prop.get());
		prop.addListener(lsnr);
		return () -> prop.removeListener(lsnr);
	}
	
	public Handle bindFloat(Consumer<Float> setter, Supplier<Float> getter, String key, String... path) {
		var prop = getFloatProperty(key, path);
		ChangeListener<? super Number> lsnr = (c,o,n) -> {
			if(!getter.get().equals(n)) {
				setter.accept((Float)n);	
			}
		};
		lsnr.changed(prop, null, prop.get());
		prop.addListener(lsnr);
		return () -> prop.removeListener(lsnr);
	}
	
	public Handle bindString(Consumer<String> setter, Supplier<String> getter, String key, String... path) {
		var prop = getStringProperty(key, path);
		ChangeListener<String> lsnr = (c,o,n) -> {
			if(!getter.get().equals(n)) {
				setter.accept(n);	
			}
		};
		lsnr.changed(prop, null, prop.get());
		prop.addListener(lsnr);
		return () -> prop.removeListener(lsnr);
	}
	
	public Handle bindStrings(Consumer<String[]> setter, Supplier<String[]> getter, String key, String... path) {
		var prop = getStringsProperty(key, path);
		ChangeListener<String[]> lsnr = (c,o,n) -> {
			if(!getter.get().equals(n)) {
				setter.accept(n);	
			}
		};
		lsnr.changed(prop, null, prop.get());
		prop.addListener(lsnr);
		return () -> prop.removeListener(lsnr);
	}
	
	public <E extends Enum<E>> Handle bindEnum(Class<E> clazz, Consumer<E> setter, Supplier<E> getter, String key, String... path) {
		var prop = getEnumProperty(clazz, key, path);
		ChangeListener<E> lsnr = (c,o,n) -> {
			if(!getter.get().equals(n)) {
				setter.accept(n);	
			}
		};
		lsnr.changed(prop, null, prop.get());
		prop.addListener(lsnr);
		return () -> prop.removeListener(lsnr);
	}
	
	public Handle bindBoolean(Consumer<Boolean> setter, Supplier<Boolean> getter, String key, String... path) {
		var prop = getBooleanProperty(key, path);
		ChangeListener<Boolean> lsnr = (c,o,n) -> {
			if(!getter.get().equals(n)) {
				setter.accept(n);	
			}
		};
		lsnr.changed(prop, null, prop.get());
		prop.addListener(lsnr);
		return () -> prop.removeListener(lsnr);
	}
	
	public INI document() {
		return ini.readOnly();
	}

	public SectionMeta meta() {
		return sectionMeta(defaultConfiguration);
	}

	public void put(String key, String value, String... path) {
		logChange(key, value, path);
		resolve(ini, path).put(key, value);
		save();
	}
	
	public void put(String key, String[] value, String... path) {
		logChange(key, value, path);
		resolve(ini, path).putAll(key, value);
		save();
	}

	public void put(String key, int value, String... path) {
		logChange(key, value, path);
		resolve(ini, path).put(key, value);
		save();
	}

	public void put(String key, float value, String... path) {
		logChange(key, value, path);
		resolve(ini, path).put(key, value);
		save();
	}

	public void put(String key, boolean value, String... path) {
		logChange(key, value, path);
		resolve(ini, path).put(key, value);
		save();
	}

	public void remove(String key, String... path) {
		resolve(ini, path).remove(key);
		save();
	}

	public void save() {
		try {
			Files.createDirectories(configuration.getParent());
			createWriter().write(ini, configuration);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public SectionMeta section(String... path) {
		return sectionMeta(defaultConfiguration.section(path));
	}

	public Optional<SectionMeta> sectionOr(String... path) {
		return defaultConfiguration.sectionOr(path).map(m -> sectionMeta(m));
	}

	public void write(Writer writer) {
		createWriter().write(ini, writer);
	}

	private INIWriter createWriter() {
		return new INIWriter.Builder().
				withMultiValueMode(MultiValueMode.SEPARATED).
				build();
	}

	private void fillValues(Data data, Map<String, ValueMeta> vals, Change defaultChange) {
		for (var key : data.keys()) {
			var desc = messageOrDefault(RESOURCES,
					data instanceof INI ? key : String.join(".", ((Section) data).path()) + "." + key);
			var tval = data.getAll(key);
			if (vals.containsKey(key)) {
				vals.put(key, new ValueMeta(key, desc, vals.get(key).defaultValue(), tval, Change.CHANGED));
			} else {
				vals.put(key, new ValueMeta(key, desc, tval, defaultChange == Change.DEFAULT ? null : tval , defaultChange));
			}
		}
	}

	private Data resolve(Data root, String... path) {
		return path.length == 0 ? root : (root.containsSection(path) ? root.section(path) : root.create(path));
	}

	private List<ObjectRef> resolveObjectProperties(String key, String... path) {
		var lst = objectProperties.get(toKey(key, path));
		if(lst == null) {
			lst = new ArrayList<>();
			objectProperties.put(toKey(key, path), lst);
		}
		return lst;
	}

	private List<StringProperty> resolveStringProperties(String key, String... path) {
		var lst = stringProperties.get(toKey(key, path));
		if(lst == null) {
			lst = new ArrayList<>();
			stringProperties.put(toKey(key, path), lst);
		}
		return lst;
	}

	private List<IntegerProperty> resolveIntegerProperties(String key, String... path) {
		var lst = integerProperties.get(toKey(key, path));
		if(lst == null) {
			lst = new ArrayList<>();
			integerProperties.put(toKey(key, path), lst);
		}
		return lst;
	}

	private List<FloatProperty> resolveFloatProperties(String key, String... path) {
		var lst = floatProperties.get(toKey(key, path));
		if(lst == null) {
			lst = new ArrayList<>();
			floatProperties.put(toKey(key, path), lst);
		}
		return lst;
	}

	private List<BooleanProperty> resolveBooleanProperties(String key, String... path) {
		var lst = booleanProperties.get(toKey(key, path));
		if(lst == null) {
			lst = new ArrayList<>();
			booleanProperties.put(toKey(key, path), lst);
		}
		return lst;
	}

	private SectionMeta sectionMeta(Data section) {
		return new SectionMeta(section instanceof INI ? new String[0] : ((Section) section).path(),
				section instanceof INI ? ""
						: messageOrDefault(RESOURCES, "section." + String.join(".", ((Section) section).path())),
				Arrays.asList(section.allSections()).stream().map(sec -> sectionMeta(sec)).toList(), values(section));
	}

	private String toKey(String key, String... path) {
		return path.length == 0 ? key : String.join(".", path) + "." + key;
	}

	private String toKey(ValueUpdateEvent evt) {
		if(evt.parent() instanceof INI)
			return evt.key();
		else {
			var sec = (Section)evt.parent();
			return String.join(".", sec.path()) + "." + evt.key();
		}
	}

	private Optional<Data> userSectionForDefaultSection(Data data) {
		if (data instanceof INI) {
			return Optional.of(ini);
		} else {
			return ini.sectionOr(((Section) data).path()).map(s -> (Data) s);
		}
	}

	private List<ValueMeta> values(Data data) {
		var vals = new LinkedHashMap<String, ValueMeta>();
		fillValues(data, vals, Change.DEFAULT);
		userSectionForDefaultSection(data).ifPresent(c -> fillValues(c, vals, Change.ADDED));
		return vals.values().stream().toList();
	}

	private void logChange(String key, Object value, String... path) {
		if(path.length == 0)
			LOG.info("Setting {} to '{}'", key, value);
		else
			LOG.info("Setting [{}].{} to '{}'", String.join(".", path), key, value);
	}

	private Builder configuredBuilder(Builder bldr) {
		bldr.withMultiValueMode(MultiValueMode.SEPARATED);
		return bldr;
	}

}
