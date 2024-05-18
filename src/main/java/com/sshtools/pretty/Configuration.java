package com.sshtools.pretty;

import static com.sshtools.jajafx.FXUtil.maybeQueue;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.jini.Data;
import com.sshtools.jini.Data.Handle;
import com.sshtools.jini.INI;
import com.sshtools.jini.INI.Section;
import com.sshtools.jini.config.INISet;
import com.sshtools.jini.config.INISet.Scope;
import com.sshtools.jini.config.Monitor;
import com.sshtools.jini.schema.INISchema;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.SingleSelectionModel;

public final class Configuration {
	static Logger LOG = LoggerFactory.getLogger(Configuration.class);

	final static ResourceBundle RESOURCES = ResourceBundle.getBundle(Configuration.class.getName());

	private final INISet iniSet;
	private final INI ini;

	Configuration(Monitor monitor) {

		iniSet = new INISet.Builder("pretty").
				withApp("pretty").
				withMonitor(monitor).
//				withDefault(Configuration.class, "Configuration.ini").
				withSchema(Configuration.class, "Configuration.schema.ini").build();

		ini = iniSet.document();

	}

	public Section transfers() {
		return ini.section(Constants.TRANSFERS_SECTION);
	}

	public Section terminal() {
		return ini.section(Constants.TERMINAL_SECTION);
	}

	public Section ui() {
		return ini.section(Constants.UI_SECTION);
	}

	public Section status() {
		return ini.section(Constants.STATUS_SECTION);
	}

	public Section debug() {
		return ini.section(Constants.DEBUG_SECTION);
	}

	public Section pricli() {
		return ini.section(Constants.PRICLI_SECTION);
	}

	public INI document() {
		return ini;
	}

	public INISchema schema() {
		return iniSet.schema();
	}

	public Path dir() {
		return iniSet.appPathForScope(Scope.USER);
	}

	public Handle bind(ObjectProperty<Integer> observable, String key, String... path) {
		var sec = resolve(ini, path);
		var vuhndl = sec.onValueUpdate(ve -> {
			maybeQueue(() -> {
				if (ve.key().equals(key))
					observable.set(sec.getInt(key));
			});
		});
		observable.setValue(sec.getInt(key));
		ChangeListener<Integer> l = (c, o, n) -> {
			sec.put(key, n);
		};
		observable.addListener(l);
		return () -> {
			observable.removeListener(l);
			vuhndl.close();
		};
	}

	public <E extends Enum<E>> Handle bind(Class<E> clazz, SingleSelectionModel<E> observable, String key,
			String... path) {
		var sec = resolve(ini, path);
		var vuhndl = sec.onValueUpdate(ve -> {
			maybeQueue(() -> {
				if (ve.key().equals(key)) {
					observable.select(sec.getEnum(clazz, key));
				}
			});
		});
		observable.select(resolve(ini, path).getEnum(clazz, key));
		ChangeListener<E> l = (c, o, n) -> {
			sec.putEnum(key, n);
		};
		observable.selectedItemProperty().addListener(l);
		return () -> {
			observable.selectedItemProperty().removeListener(l);
			vuhndl.close();
		};

	}

	public Handle bind(BooleanProperty observable, String key, String... path) {
		var sec = resolve(ini, path);
		var vuhndl = sec.onValueUpdate(ve -> {
			maybeQueue(() -> {
				if (ve.key().equals(key))
					observable.set(sec.getBoolean(key));
			});
		});
		observable.setValue(sec.getBoolean(key));
		ChangeListener<Boolean> l = (c, o, n) -> {
			sec.put(key, n);
		};
		observable.addListener(l);
		return () -> {
			observable.removeListener(l);
			vuhndl.close();
		};
	}

	public Handle bind(DoubleProperty observable, String key, String... path) {
		var sec = resolve(ini, path);
		var vuhndl = sec.onValueUpdate(ve -> {
			maybeQueue(() -> {
				if (ve.key().equals(key))
					observable.set(sec.getDouble(key));
			});
		});
		observable.setValue(sec.getDouble(key));
		ChangeListener<Number> l = (c, o, n) -> {
			sec.put(key, n.doubleValue());
		};
		observable.addListener(l);
		return () -> {
			observable.removeListener(l);
			vuhndl.close();
		};
	}

	public Handle bindInteger(Consumer<Integer> setter, Supplier<Integer> getter, String key, String... path) {
		var sec = resolve(ini, path);
		var vuhndl = sec.onValueUpdate(ve -> {
			maybeQueue(() -> {
				if (ve.key().equals(key)) {
					var val = sec.getInt(key);
					if (getter == null || !Objects.equals(val, getter.get())) {
						setter.accept(val);
					}
				}
			});
		});
		setter.accept(sec.getInt(key));
		return () -> {
			vuhndl.close();
		};
	}

	public Handle bindFloat(Consumer<Float> setter, Supplier<Float> getter, String key, String... path) {
		var sec = resolve(ini, path);
		var vuhndl = sec.onValueUpdate(ve -> {
			maybeQueue(() -> {
				if (ve.key().equals(key)) {
					var val = sec.getFloat(key);
					if (getter == null || !Objects.equals(val, getter.get())) {
						setter.accept(val);
					}
				}
			});
		});
		setter.accept(sec.getFloat(key));
		return () -> {
			vuhndl.close();
		};
	}

	public Handle bindString(Consumer<String> setter, Supplier<String> getter, String key, String... path) {
		var sec = resolve(ini, path);
		var vuhndl = sec.onValueUpdate(ve -> {
			maybeQueue(() -> {
				if (ve.key().equals(key)) {
					var val = sec.get(key);
					if (getter == null || !Objects.equals(val, getter.get())) {
						setter.accept(val);
					}
				}
			});
		});
		setter.accept(sec.get(key));
		return () -> {
			vuhndl.close();
		};
	}

	public Handle bindStrings(Consumer<String[]> setter, Supplier<String[]> getter, String key, String... path) {
		var sec = resolve(ini, path);
		var vuhndl = sec.onValueUpdate(ve -> {
			maybeQueue(() -> {
				if (ve.key().equals(key)) {
					var val = sec.getAll(key);
					if (getter == null || !Arrays.equals(val, getter.get())) {
						setter.accept(val);
					}
				}
			});
		});
		setter.accept(sec.getAll(key));
		return () -> {
			vuhndl.close();
		};
	}

	public <E extends Enum<E>> Handle bindEnum(Class<E> clazz, Consumer<E> setter, Supplier<E> getter, String key,
			String... path) {
		var sec = resolve(ini, path);
		var vuhndl = sec.onValueUpdate(ve -> {
			maybeQueue(() -> {
				if (ve.key().equals(key)) {
					var val = sec.getEnum(clazz, key);
					if (getter == null || !Objects.equals(val, getter.get())) {
						setter.accept(val);
					}
				}
			});
		});
		setter.accept(sec.getEnum(clazz, key));
		return () -> {
			vuhndl.close();
		};
	}

	public Handle bindBoolean(Consumer<Boolean> setter, Supplier<Boolean> getter, String key, String... path) {
		var sec = resolve(ini, path);
		var vuhndl = sec.onValueUpdate(ve -> {
			maybeQueue(() -> {
				if (ve.key().equals(key)) {
					var val = sec.getBoolean(key);
					if (getter == null || !Objects.equals(val, getter.get())) {
						setter.accept(val);
					}
				}
			});
		});
		setter.accept(sec.getBoolean(key));
		return () -> {
			vuhndl.close();
		};
	}

	private Data resolve(Data root, String... path) {
		return path.length == 0 ? root : (root.containsSection(path) ? root.section(path) : root.create(path));
	}

}
