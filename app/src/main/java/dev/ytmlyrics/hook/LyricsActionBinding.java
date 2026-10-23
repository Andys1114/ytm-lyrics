package dev.ytmlyrics.hook;

import java.util.WeakHashMap;

/** Keep only the lyric action active; restore native values before a recycled view changes role. */
final class LyricsActionBinding<T> {
    interface Port<T> {
        boolean matches(T view);
        boolean enabled(T view);
        boolean clickable(T view);
        float alpha(T view);
        void apply(T view,boolean enabled,boolean clickable,float alpha);
    }
    private static final class State {
        boolean enabled,clickable;float alpha;
        State(boolean enabled,boolean clickable,float alpha){this.enabled=enabled;this.clickable=clickable;this.alpha=alpha;}
    }
    private final Port<T> port;
    private final WeakHashMap<T,State> states=new WeakHashMap<>();
    private boolean writing;
    LyricsActionBinding(Port<T> port){this.port=port;}
    private State snapshot(T view){return new State(port.enabled(view),port.clickable(view),port.alpha(view));}
    void beforeLabel(T view) {
        State state=states.remove(view);
        if(state!=null)apply(view,state.enabled,state.clickable,state.alpha);
    }
    void afterLabel(T view) {
        if(!port.matches(view))return;
        states.put(view,snapshot(view));apply(view,true,true,1f);
    }
    Object requested(T view,String property,Object value) {
        if(writing || !port.matches(view))return value;
        State state=states.computeIfAbsent(view,this::snapshot);
        switch(property) {
            case "setEnabled" -> state.enabled=(Boolean)value;
            case "setClickable" -> state.clickable=(Boolean)value;
            case "setAlpha" -> state.alpha=(Float)value;
            default -> throw new IllegalArgumentException(property);
        }
        return property.equals("setAlpha")?(Object)1f:Boolean.TRUE;
    }
    private void apply(T view,boolean enabled,boolean clickable,float alpha) {
        writing=true;
        try{port.apply(view,enabled,clickable,alpha);}finally{writing=false;}
    }
}
