
public class Sleep {

	public void doWait() {
		
		synchronized (this) {
			
			try {
				
				this.wait();
				
			} catch (InterruptedException e) {
				
				e.printStackTrace();
				
			}
			
		}
		
	}
	
	public void doNotify() {
		
		synchronized (this) {
			
			this.notify();
			
		}
		
	}
	
}
