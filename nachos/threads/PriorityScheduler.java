package nachos.threads;

import nachos.machine.*;
import nachos.threads.KThread;
import java.util.LinkedList;
import java.util.HashSet;

/**
 * A scheduler that chooses threads based on their priorities.
 * 
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the thread
 * that has been waiting longest.
 * 
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has the
 * potential to starve a thread if there's always a thread waiting with higher
 * priority.
 * 
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
	/**
	 * Allocate a new priority scheduler.
	 */
	public PriorityScheduler() {
	}

	/**
	 * Allocate a new priority thread queue.
	 * 
	 * @param transferPriority <tt>true</tt> if this queue should transfer
	 * priority from waiting threads to the owning thread.
	 * @return a new priority thread queue.
	 */
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new PriorityQueue(transferPriority);
	}

	public int getPriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getPriority();
	}

	public int getEffectivePriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getEffectivePriority();
	}

	public void setPriority(KThread thread, int priority) {
		Lib.assertTrue(Machine.interrupt().disabled());

		Lib.assertTrue(priority >= priorityMinimum
				&& priority <= priorityMaximum);

		getThreadState(thread).setPriority(priority);
	}

	public boolean increasePriority() {
		boolean intStatus = Machine.interrupt().disable();
		boolean ret = true;

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMaximum)
			ret = false;
		else
			setPriority(thread, priority + 1);

		Machine.interrupt().restore(intStatus);
		return ret;
	}

	public boolean decreasePriority() {
		boolean intStatus = Machine.interrupt().disable();
		boolean ret = true;

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMinimum)
			ret = false;
		else
			setPriority(thread, priority - 1);

		Machine.interrupt().restore(intStatus);
		return ret;
	}

	/**
	 * The default priority for a new thread. Do not change this value.
	 */
	public static final int priorityDefault = 1;

	/**
	 * The minimum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMinimum = 0;

	/**
	 * The maximum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMaximum = 7;

	/**
	 * Return the scheduling state of the specified thread.
	 * 
	 * @param thread the thread whose scheduling state to return.
	 * @return the scheduling state of the specified thread.
	 */
	protected ThreadState getThreadState(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new ThreadState(thread);

		return (ThreadState) thread.schedulingState;
	}

	/**
	 * A <tt>ThreadQueue</tt> that sorts threads by priority.
	 */
	protected class PriorityQueue extends ThreadQueue {
		PriorityQueue(boolean transferPriority) {
			this.transferPriority = transferPriority;
		}

		public void waitForAccess(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).waitForAccess(this);
		}

		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).acquire(this);
		}

		public KThread nextThread() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me
			if(transferPriority)
				getThreadState(acqThread).release(this);
			
			if(pQueue.isEmpty())
				return null;
			
			KThread nextThread = pQueue.removeFirst();
			
			getThreadState(nextThread).acquire(this);
			
			return nextThread;
		}

		/**
		 * Return the next thread that <tt>nextThread()</tt> would return,
		 * without modifying the state of this queue.
		 * 
		 * @return the next thread that <tt>nextThread()</tt> would return.
		 */
		protected ThreadState pickNextThread() {
			// implement me
			if(pQueue.isEmpty())
				return null;
			
			ThreadState nxtThrdState;
			
			nxtThrdState = getThreadState(pQueue.peekFirst());
			return nxtThrdState;
		}

		public void print() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me (if you want)
		}

		/**
		 * <tt>true</tt> if this queue should transfer priority from waiting
		 * threads to the owning thread.
		 */
		public boolean transferPriority;
		/* Queue of threads*/
		private LinkedList<KThread> pQueue = new LinkedList<KThread>();
		
		/* Acquired thread*/
		private KThread acqThread;
		
	}

	/**
	 * The scheduling state of a thread. This should include the thread's
	 * priority, its effective priority, any objects it owns, and the queue it's
	 * waiting for, if any.
	 * 
	 * @see nachos.threads.KThread#schedulingState
	 */
	protected class ThreadState {
		/**
		 * Allocate a new <tt>ThreadState</tt> object and associate it with the
		 * specified thread.
		 * 
		 * @param thread the thread this state belongs to.
		 */
		public ThreadState(KThread thread) {
			this.thread = thread;

			wQueueList = new HashSet<PriorityQueue>();
			aQueueList = new HashSet<PriorityQueue>();
			setPriority(priorityDefault);
			effectivePriority = priority;
		}

		/**
		 * Return the priority of the associated thread.
		 * 
		 * @return the priority of the associated thread.
		 */
		public int getPriority() {
			return priority;
		}

		/**
		 * Return the effective priority of the associated thread.
		 * 
		 * @return the effective priority of the associated thread.
		 */
		public int getEffectivePriority() {
			// implement me
			return effectivePriority;
		}

		/**
		 * Set the priority of the associated thread to the specified value.
		 * 
		 * @param priority the new priority.
		 */
		public void setPriority(int priority) {
			if (this.priority == priority)
				return;

			this.priority = priority;
			
			this.effectivePriority = calculateEffectivePriority();

			// implement me
			/*change the position of thread in all the wait queues and change
			 * the effective priority of the owners of the wait queues if required
			 */
			if(!wQueueList.isEmpty())
			{
				for(PriorityQueue wQueue : wQueueList)
				{
					wQueue.pQueue.remove(this.thread);
					insertIntoWaitQueue(wQueue);
					
					ThreadState ownerState = getThreadState(wQueue.acqThread);
					int ownerPriority = ownerState.getEffectivePriority();
					
					if(ownerPriority < this.effectivePriority)
					{
						ownerState.setEffectivePriority(this.effectivePriority);
						ownerState.reshuffleWaitQueues();
					}
				}
			}
		}

		/**
		 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
		 * the associated thread) is invoked on the specified priority queue.
		 * The associated thread is therefore waiting for access to the resource
		 * guarded by <tt>waitQueue</tt>. This method is only called if the
		 * associated thread cannot immediately obtain access.
		 * 
		 * @param waitQueue the queue that the associated thread is now waiting
		 * on.
		 * 
		 * @see nachos.threads.ThreadQueue#waitForAccess
		 */
		public void waitForAccess(PriorityQueue waitQueue) {
			// implement me
			
//			if(priority > effectivePriority)
//				effectivePriority = priority;
			
			insertIntoWaitQueue(waitQueue);
			
			wQueueList.add(waitQueue);
			
			if(waitQueue.transferPriority)
			{
				KThread owner = waitQueue.acqThread;
				ThreadState ownerState = getThreadState(owner);
				
				int ownerPriority = ownerState.effectivePriority;
				
				if(ownerPriority < this.effectivePriority)
				{
					ownerState.setEffectivePriority(this.effectivePriority);
					ownerState.reshuffleWaitQueues();
				}
			}
		}

		/**
		 * Called when the associated thread has acquired access to whatever is
		 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
		 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
		 * <tt>thread</tt> is the associated thread), or as a result of
		 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
		 * 
		 * @see nachos.threads.ThreadQueue#acquire
		 * @see nachos.threads.ThreadQueue#nextThread
		 */
		public void acquire(PriorityQueue waitQueue) {
			// implement me
			if(!wQueueList.isEmpty())
				wQueueList.remove(waitQueue);
			
			waitQueue.acqThread = this.thread;
			
			if(waitQueue.transferPriority)
			{
				aQueueList.add(waitQueue);
				
//				if(effectivePriority < priority)
//					effectivePriority = priority;
			}
		}
		
		public void release(PriorityQueue waitQueue)
		{
			waitQueue.acqThread = null;
			
			if(waitQueue.transferPriority)
			{
				aQueueList.remove(waitQueue);
				effectivePriority = this.calculateEffectivePriority();
				this.reshuffleWaitQueues();
			}		
		}
		
		private void insertIntoWaitQueue(PriorityQueue waitQueue)
		{
			int i;
			
			for(i = 0; i < waitQueue.pQueue.size(); i++)
			{
				if(getThreadState(waitQueue.pQueue.get(i)).effectivePriority < this.effectivePriority)
					break;
			}
			
			waitQueue.pQueue.add(i, this.thread);
		}
		
		private void setEffectivePriority(int ePriority)
		{
			effectivePriority = ePriority;
		}
		
		private void reshuffleWaitQueues()
		{
			if(!wQueueList.isEmpty())
			{
				for(PriorityQueue wQueue : wQueueList)
				{
					wQueue.pQueue.remove(this.thread);
					insertIntoWaitQueue(wQueue);
					
					/*recalculate effective priority for owner of queues*/
					ThreadState ownerState = getThreadState(wQueue.acqThread);
					int ownerPriority = ownerState.getEffectivePriority();
					if(ownerPriority < this.effectivePriority)
						ownerState.setEffectivePriority(this.effectivePriority);
				}
			}
		}
		
		private int calculateEffectivePriority()
		{
			int highestPriority = priority;
			
			if(!aQueueList.isEmpty())
			{
				for(PriorityQueue aQueue : aQueueList)
				{
					if(!aQueue.pQueue.isEmpty())
					{
						KThread qThread = aQueue.pQueue.getFirst();
						int qPriority = getThreadState(qThread).effectivePriority; 
						if(qPriority > highestPriority)
							highestPriority = qPriority;
					}
				}
			}
			
			return highestPriority;
		}

		/** The thread with which this object is associated. */
		protected KThread thread;

		/** The priority of the associated thread. */
		protected int priority;
		
		/*List of Wait queues acquired by thread*/
		private HashSet<PriorityQueue> aQueueList;
		
		/*List of Wait queues waited by thread*/
		private HashSet<PriorityQueue> wQueueList;
		
		/*Effective priority*/
		private int effectivePriority;
	}
	
	static void selfTest()
	{
		Lock l = new Lock();
		KThread thrd1 = new KThread(new PingTest("Thread 1", 1, 5, l));
		KThread thrd2 = new KThread(new PingTest("Thread 2", 3, 5, null));
		KThread thrd3 = new KThread(new PingTest("Thread 3", 4, 5, l));
		
		boolean intStatus = Machine.interrupt().disable();
//		ThreadedKernel.scheduler.setPriority(priorityMaximum);
		ThreadedKernel.scheduler.setPriority(thrd1, 1);
		ThreadedKernel.scheduler.setPriority(thrd2, 3);
		ThreadedKernel.scheduler.setPriority(thrd3, 4);
//		
		Machine.interrupt().restore(intStatus);
		
		thrd1.fork();
		KThread.yield();
		thrd2.fork();
		thrd3.fork();
		new PingTest("Thread 0", 1, 5, null).run();
		//KThread.currentThread().sleep();
//		thrd1.join();
//		thrd2.join();
	}
	
	private static class PingTest implements Runnable {
		PingTest(String which, int priority, int n, Lock l) {
			this.which = which;
			p = priority;
			this.n = n;
			this.l = l;
		}

		public void run() {
			for (int i = 0; i < n; i++) {
				if(i == 0)
				{
//					boolean intStatus = Machine.interrupt().disable();
					if(l != null)
						l.acquire();
//					ThreadedKernel.scheduler.setPriority(p);
//					
//					System.out.println(which + " priority set to " + p);
					
//					Machine.interrupt().restore(intStatus);
				}
				System.out.println(which + " looped " + i
						+ " times");
				KThread.yield();
			}
			if(l != null)
				l.release();
		}

		private String which;
		private int p;
		private int n;
		private Lock l;
	}
}
