package io.tuji.app;
import java.io.*;
/** All operations share one lock; active task ownership protects the entire jobs root. */
public final class TaskCache {
 private final File root;private boolean active;private final java.util.Set<String> owned=new java.util.HashSet<>();
 public TaskCache(File root){this.root=root;root.mkdirs();}
 public synchronized void active(boolean value){active=value;if(!value)owned.clear();}
 public synchronized File allocate(String prefix){File f=new File(root,prefix+java.util.UUID.randomUUID());owned.add(f.getName());return f;}
 public synchronized long[] stats(){long total=size(root);long used=0;if(active){for(String name:owned)used+=size(new File(root,name));}return new long[]{total,used,Math.max(0,total-used)};}
 public synchronized long clean(){File[] children=root.listFiles();if(children!=null)for(File f:children)if(!active||!owned.contains(f.getName()))remove(f);return size(root);}
 private void remove(File f){try{String base=root.getCanonicalPath()+File.separator;if(!f.getCanonicalPath().startsWith(base))return;File[] children=f.listFiles();if(children!=null)for(File c:children)remove(c);f.delete();}catch(IOException ignored){}}
 private long size(File f){if(!f.exists())return 0;try{if(!f.getCanonicalPath().equals(root.getCanonicalPath())&&!f.getCanonicalPath().startsWith(root.getCanonicalPath()+File.separator))return 0;}catch(IOException e){return 0;}if(f.isFile())return f.length();long n=0;File[] cs=f.listFiles();if(cs!=null)for(File c:cs)n+=size(c);return n;}
}
