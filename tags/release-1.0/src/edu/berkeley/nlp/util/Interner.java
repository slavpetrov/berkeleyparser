package edu.berkeley.nlp.util;

import java.util.Collection;
import java.util.Map;

/**
 * Canonicalizes objects.  Given an object, the intern() method returns a
 * canonical representation of that object, that is, an object which equals()
 * the input.  Furthermore, given two objects x and y, it is guaranteed that if
 * x.equals(y), then intern(x) == intern(y).  The default behavior is that the
 * interner is backed by a HashMap and the canonical version of an object x is
 * simply the first object that equals(x) which is passed to the interner.  In
 * this case, it can be true that intern(x) == x.  The backing map can be
 * specified by passing a MapFactory on construction (though the only standard
 * option which makes much sense is the WeakHashMap, which is slower than a
 * HashMap, but which allows unneeded keys to be reclaimed by the garbage
 * collector).  The source of canonical elements can be changed by specifying an
 * Interner.Factory on construction.
 *
 * @author Dan Klein
 */
public class Interner <T> {
  /**
   * The source of canonical objects when a non-interned object is presented to
   * the interner.  The default implementation is an identity map.
   */
  public static interface CanonicalFactory <T> {
    T build(T object);
  }

  static class IdentityCanonicalFactory <T> implements CanonicalFactory<T> {
    public T build(T object) {
      return object;
    }
  }

  Map<T, T> canonicalMap;
  CanonicalFactory<T> cf;

  /**
   * Returns a canonical representation of the given object.  If the object has
   * no canonical representation, one is built using the interner's
   * CanonicalFactory.  The default is that new objects will be their own
   * canonical instances.
   *
   * @param object
   * @return a canonical representation of that object
   */
  public T intern(T object) {
    T canonical = canonicalMap.get(object);
    if (canonical == null) {
      canonical = cf.build(object);
      canonicalMap.put(canonical, canonical);
    }
    return canonical;
  }
  /**
   * Does the interner already have
   * a canonical copy of this object?
   * 
   * @param object
   * @return whether or not the interner contains the object 
   * @author aria42
   */
  public boolean contains(T object) {
	  return canonicalMap.containsKey(object);
  }
  
  /**
   * How many interned objects are there?
   * 
   * @param size 
   * @author aria42
   */
  public int size() {
	  return canonicalMap.size();
  }
  
  /**
   * Returns a collection of the canonical objects
   * in the interner 
   * @return canonicalObjects 
   * @author aria42
   */
  public Collection<T> getCanonicalObjects() {
	  return canonicalMap.values();
  }
  
  public Interner() {
    this(new MapFactory.HashMapFactory<T,T>(), new IdentityCanonicalFactory<T>());
  }

  public Interner(MapFactory<T,T> mf) {
    this(mf, new IdentityCanonicalFactory<T>());
  }

  public Interner(CanonicalFactory<T> f) {
    this(new MapFactory.HashMapFactory<T,T>(), f);
  }

  public Interner(MapFactory<T,T> mf, CanonicalFactory<T> cf) {
    canonicalMap = mf.buildMap();
    this.cf = cf;
  }
  /**
   * Clear the contents of the interner 
   * @author aria42
   */
  public void clear() {
	  canonicalMap.clear();
  }
  
}
