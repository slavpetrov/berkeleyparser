package edu.berkeley.nlp.mapper;


public interface MapWorkerFactory<Item> {
	public MapWorker<Item> newMapWorker();

	public static class DefaultFactory<Item> implements MapWorkerFactory<Item> {

		private Class c;

		public DefaultFactory(Class c) {
			this.c = c;
		}

		public MapWorker<Item> newMapWorker() {
			// TODO Auto-generated method stub
			try {
				Object o = c.newInstance();
				return (MapWorker<Item>) o;
			} catch (Exception e) {
				e.printStackTrace();
			}
			throw new RuntimeException();
		}

	}
}
