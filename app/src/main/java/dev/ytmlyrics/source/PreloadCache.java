package dev.ytmlyrics.source;

import android.content.SharedPreferences;
import dev.ytmlyrics.core.Candidate;
import dev.ytmlyrics.core.Matching;
import dev.ytmlyrics.core.Song;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.LongSupplier;
import org.json.JSONArray;
import org.json.JSONObject;

/** Automatic candidate pools, separate from manual bindings. Missing queue duration is never invented. */
public final class PreloadCache {
    private static final long TTL=24*60*60*1000L;
    private static final int MAX_ITEMS=30,MAX_BYTES=4*1024*1024;
    private static final String PREFIX="pool:";
    public record Pool(List<Candidate> candidates,boolean complete) {
        public Candidate best(Song song) {
            Candidate best=null;
            for(var value:candidates)if(Matching.reliable(song,value) && (best==null || Matching.compare(song,value,best)>0))best=value;
            return best;
        }
    }
    private final SharedPreferences prefs;
    private final LongSupplier clock;
    public PreloadCache(SharedPreferences prefs){this(prefs,System::currentTimeMillis);}
    PreloadCache(SharedPreferences prefs,LongSupplier clock){this.prefs=prefs;this.clock=clock;}
    public synchronized Pool get(Song song,String policy) {
        String key=PREFIX+song.key(),raw=prefs.getString(key,null);
        if(raw==null)return null;
        try {
            var entry=new JSONObject(raw);
            long age=clock.getAsLong()-entry.getLong("time");
            if(age<0 || age>=TTL){prefs.edit().remove(key).apply();return null;}
            if(!policy.equals(entry.getString("policy")) || !Matching.normalize(song.title()).equals(Matching.normalize(entry.getString("title")))
                    || !Matching.normalize(song.artist()).equals(Matching.normalize(entry.getString("artist"))))return null;
            var array=entry.getJSONArray("candidates");var candidates=new ArrayList<Candidate>();
            for(int i=0;i<array.length();i++)candidates.add(BindingStore.decode(array.getJSONObject(i)));
            return new Pool(List.copyOf(candidates),entry.optBoolean("complete"));
        }catch(org.json.JSONException|IllegalArgumentException error){prefs.edit().remove(key).apply();return null;}
    }
    public synchronized void put(Song song,String policy,List<Candidate> candidates,boolean complete) {
        try {
            var data=new JSONArray();
            for(var candidate:candidates)if(candidate.hasLyrics())data.put(BindingStore.encode(candidate));
            if(data.length()==0)return;
            String raw=new JSONObject().put("title",song.title()).put("artist",song.artist()).put("policy",policy)
                    .put("time",clock.getAsLong()).put("complete",complete).put("candidates",data).toString();
            int size=raw.getBytes(StandardCharsets.UTF_8).length;
            if(size>MAX_BYTES)return;
            var editor=prefs.edit();var others=new ArrayList<java.util.Map.Entry<String,?>>();
            for(var entry:prefs.getAll().entrySet())if(entry.getKey().startsWith(PREFIX) && !entry.getKey().equals(PREFIX+song.key()))others.add(entry);
            others.sort(Comparator.comparingLong(entry->time(entry.getValue())));
            int total=size;for(var entry:others)total+=entry.getValue().toString().getBytes(StandardCharsets.UTF_8).length;
            int count=others.size()+1;
            for(var entry:others) {
                if(count<=MAX_ITEMS && total<=MAX_BYTES)break;
                editor.remove(entry.getKey());count--;total-=entry.getValue().toString().getBytes(StandardCharsets.UTF_8).length;
            }
            editor.putString(PREFIX+song.key(),raw).apply();
        }catch(org.json.JSONException error){throw new IllegalArgumentException("预载缓存无法保存",error);}
    }
    private static long time(Object raw){try{return new JSONObject(raw.toString()).optLong("time");}catch(org.json.JSONException error){return 0;}}
    public synchronized void remove(Song song){prefs.edit().remove(PREFIX+song.key()).apply();}
}
