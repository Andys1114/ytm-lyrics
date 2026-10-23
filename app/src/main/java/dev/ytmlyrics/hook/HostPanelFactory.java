package dev.ytmlyrics.hook;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Builds the same native panel schema used by YTM, with an independently registered ID. */
final class HostPanelFactory {
    static final String ID = "dev.ytmlyrics.lyrics-panel";
    private final ClassLoader loader;
    HostPanelFactory(ClassLoader loader) { this.loader = loader; }

    Object model(String title) throws ReflectiveOperationException {
        // akpi's wire value for WATCH is 1; JADX's enum name is not its obfuscated field name.
        Object identifier = new Proto("akpj", "a").set("b", 3).set("c", 1).set("d", ID).build();
        Object text = new Proto("akwe", "a").set("b", 1).set("d", title).build();
        Object headerContent = new Proto("akpu", "a").set("b", 2).set("e", text).build();
        Object header = new Proto("akpo", "a").set("b", 138681548).set("c", headerContent).build();
        Object body = new Proto("akpn", "a").set("b", 49399797).set("c", type("apjo").getField("a").get(null)).build();
        return new Proto("akpp", "b").set("e", 18).set("f", identifier)
                .set("g", header).set("h", body).set("m", true).set("D", 95102)
                .set("A", true).set("B", true)
                .set("c", 2 | 4 | 128 | 16777216 | 2097152 | 4194304).build();
    }
    Object endpoint() throws ReflectiveOperationException {
        Object identifier = new Proto("akpj", "a").set("b", 3).set("c", 1).set("d", ID).build();
        Object show = new Proto("apsq", "a").set("d", 10).set("e", identifier).build();
        Proto endpoint = new Proto("ajrz", "a");
        Object extension = type("apsq").getField("b").get(null);
        Method setExtension = endpoint.builder.getClass().getMethod("e", type("aich"), Object.class);
        setExtension.setAccessible(true);
        setExtension.invoke(endpoint.builder, extension, show);
        return endpoint.build();
    }
    Class<?> type(String name) throws ClassNotFoundException { return Class.forName(name, false, loader); }
    static Object get(Object value, String name) throws ReflectiveOperationException {
        for (Class<?> cls = value.getClass(); cls != null; cls = cls.getSuperclass()) {
            try { Field field = cls.getDeclaredField(name); field.setAccessible(true); return field.get(value); }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(value.getClass().getName() + "." + name);
    }
    private final class Proto {
        final Object builder;
        Proto(String name, String defaultField) throws ReflectiveOperationException {
            Object defaultInstance = type(name).getField(defaultField).get(null);
            builder = call(defaultInstance, "createBuilder");
        }
        Proto set(String name, Object value) throws ReflectiveOperationException {
            call(builder, "copyOnWrite");
            Object instance = get(builder, "instance");
            Field field = instance.getClass().getField(name);
            field.setAccessible(true); field.set(instance, value);
            return this;
        }
        Object build() throws ReflectiveOperationException { return call(builder, "build"); }
    }
    static Object call(Object object, String name) throws ReflectiveOperationException {
        Method method = object.getClass().getMethod(name); method.setAccessible(true); return method.invoke(object);
    }
}
