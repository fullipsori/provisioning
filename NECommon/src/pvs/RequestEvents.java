package pvs;

@SuppressWarnings("serial")
public class RequestEvents implements java.io.Serializable {
	private String eventMessage = null;

	public RequestEvents() {
	}

	public RequestEvents(String eventString) {
		this.eventMessage = eventString;
	}

	public String getEventMessage() {
		return eventMessage;
	}

	public void setEventMessage(String eventMessage) {
		this.eventMessage = eventMessage;
	}
	
}
