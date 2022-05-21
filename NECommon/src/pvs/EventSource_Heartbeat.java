package pvs;

import com.lguplus.pvs.Registry;
import com.tibco.bw.palette.shared.java.JavaProcessStarter;

public class EventSource_Heartbeat extends JavaProcessStarter {
	
	HeartbeatThread heartbeatThread = null;
	
	@Override
	public void init() throws Exception {
		heartbeatThread = new HeartbeatThread(this);
	}

	@Override
	public void onShutdown() {
		
	}

	@Override
	public void onStart() throws Exception {
		Thread thread = new Thread(this.heartbeatThread);
		thread.start();
	}

	@Override
	public void onStop() throws Exception {
		this.heartbeatThread.setFlowControlEnabled(true);
	}

	public static class HeartbeatThread implements Runnable {
		JavaProcessStarter javaProcessStarter;
		boolean flowControl = false;
		public HeartbeatThread(JavaProcessStarter javaProcessStarter) {
			this.javaProcessStarter = javaProcessStarter;
		}
		
		@Override
		public void run() {
			while(true) {
				try {
					String connectionId = Registry.getInstance().heartbeatQueue.take();
					if(Registry.getInstance().needHeartBeat(connectionId)) {
						this.javaProcessStarter.onEvent(new RequestVo(connectionId));
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		
		public void setFlowControlEnabled(boolean flowControl) {
			this.flowControl = flowControl;
		}
		
	}
}
