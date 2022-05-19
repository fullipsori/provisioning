package pvs;

@SuppressWarnings("serial")
public class RequestVo implements java.io.Serializable {
	private String connectionId = null;

	public RequestVo() {
	}

	public RequestVo(String connectionId) {
		this.connectionId = connectionId;
	}

	public String getConnectionId() {
		return connectionId;
	}

	public void setConnectionId(String connectionId) {
		this.connectionId = connectionId;
	}
	
}
