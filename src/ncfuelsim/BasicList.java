package ncfuelsim;

import java.util.ArrayList;
import java.util.Collection;

class BasicList<E> extends ArrayList<E> {
	
	BasicList() {
		super();
	}
	
	BasicList(Collection<? extends E> coll) {
		super(coll);
	}
	
	@Override
	public String toString() {
		String out = "";
		
		for (int i = 0; i < size(); i++) {
			out = out + ", " + get(i).toString();
		}
		return out.substring(2);
	}
	
	private static final long serialVersionUID = -8950908686309848450L;
	
}
