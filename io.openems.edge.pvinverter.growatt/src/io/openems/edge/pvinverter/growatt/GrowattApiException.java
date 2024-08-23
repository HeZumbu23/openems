package io.openems.edge.pvinverter.growatt;

public class GrowattApiException extends Exception {

	private static final long serialVersionUID = -3852580834105688521L;
	
	public GrowattApiException(String message) {
        super(message);
    }

	public GrowattApiException(String message, Exception ex) {
        super(message, ex);
    }
}
