package dev.ytmlyrics.hook;

/** Nested drawing inherits the lyric action's color, and always leaves unrelated draws untouched. */
final class ActionDrawScope {
    interface Work { Object draw() throws Throwable; }
    private final ThreadLocal<Integer> color=new ThreadLocal<>();
    Integer color(){return color.get();}
    Object draw(Integer requested,Work work) throws Throwable {
        Integer previous=color.get();
        if(requested!=null)color.set(requested);
        try{return work.draw();}
        finally{if(previous==null)color.remove();else color.set(previous);}
    }
}
