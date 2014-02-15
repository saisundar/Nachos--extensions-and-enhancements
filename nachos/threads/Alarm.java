package nachos.threads;
  
import nachos.machine.*;

import java.util.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm
{
	
	//LinkedList<Nestedclass> list = new LinkedList<Nestedclass>();
	PriorityQueue<Nestedclass> pqueue = new PriorityQueue<Nestedclass>(10, new Comparator<Object>(){
		@Override
		public int compare(Object o1, Object o2) {
			// TODO Auto-generated method stub
			Nestedclass arg0 = (Nestedclass)o1;
			Nestedclass arg1 = (Nestedclass)o2;
			return (int)(arg0.exp_time - arg1.exp_time);
		}
	
	});
	
	public class Nestedclass
	{
		private KThread thread_name;
		private long exp_time;
	
		public Nestedclass(KThread name, long exp)
		{
			thread_name = name;
			exp_time = exp;
		}
		
		public KThread Thread_name ()
		{
			return thread_name;
		}
		
		public long Expire_time ()
		{
			return exp_time;
		}
		

		
	}
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() 
	{
		while(!pqueue.isEmpty() && Machine.timer().getTime()>=pqueue.peek().Expire_time() )
		{
			Nestedclass obj = pqueue.poll();
			obj.Thread_name().ready();
		}
	}
	
	

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		// for now, cheat just to get something working (busy waiting is bad)
		//long wakeTime = Machine.timer().getTime() + x;
		//while (wakeTime > Machine.timer().getTime())
			//KThread.yield();
		Machine.interrupt().disable();   
		Nestedclass obj = new Nestedclass(KThread.currentThread(), Machine.timer().getTime() + x);
		pqueue.add(obj);
		KThread.sleep();
		Machine.interrupt().enable();
	}
	
	public static class test implements Runnable{
		Alarm alarm;
		private int number;
		test(int number, Alarm alarm){
			this.number=number;
			this.alarm=alarm;
		}
		public void run(){
			System.out.println("thread" + number + "started" + "at" + Machine.timer().getTime());
			alarm.waitUntil(number);
			System.out.println("thread" + number + "ran" + "at" + Machine.timer().getTime());
		}
	}
   public static void selftest()
   {
	   
	   Alarm alarm = new Alarm();
	   KThread t1= new KThread(new test(1000,alarm));
	   t1.join();
	   KThread t2= new KThread(new test(1000,alarm));
	   
	   t2.join();
	   t1.fork();
	   t2.fork();
	   
	  //KThread t3= new KThread(new test(5000,alarm));
	   //t3.fork();
	   
	   
   }
}