package edu.berkeley.nlp.mapper;

import java.util.ArrayList;
import java.util.List;

/**
 * The only method you need to implement is map(Item item);
 * 
 * @author aria42
 * 
 * @param <Item>
 */
public abstract class MapWorker<Item> implements Runnable, SimpleMapper<Item> {
	protected List<Item> items = new ArrayList<Item>();

	public void setItems(List<Item> items) {
		this.items = items;
	}

	public void addItem(Item item) {
		items.add(item);
	}

	public void run() {
		for (Item item : items) {
			map(item);
		}
	}

	public void reduce() {
	}
}
