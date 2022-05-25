package pvs;

import com.lguplus.pvs.Registry;
import com.tibco.bw.palette.shared.java.JavaProcessStarter;

public class EventSource_OpenConnection extends JavaProcessStarter {
	
	MyThread myThread = null;
	
	@Override
	public void init() throws Exception {
		myThread = new MyThread(this);
	}

	@Override
	public void onShutdown() {
		
	}

	@Override
	public void onStart() throws Exception {
		Thread thread = new Thread(this.myThread);
		thread.start();
	}

	@Override
	public void onStop() throws Exception {
		this.myThread.setFlowControlEnabled(true);
	}

	public static class MyThread implements Runnable {
		JavaProcessStarter javaProcessStarter;
		boolean flowControl = false;
		public MyThread(JavaProcessStarter javaProcessStarter) {
			this.javaProcessStarter = javaProcessStarter;
		}
		
		@Override
		public void run() {
			while(true) {
				try {
					this.javaProcessStarter.onEvent(new RequestVo(Registry.getInstance().takeConnRequest()));
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
