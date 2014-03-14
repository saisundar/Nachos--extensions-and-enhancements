package nachos.threads;

import nachos.machine.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Random;
import java.util.HashSet;

/**
 * A scheduler that chooses threads using a lottery.
 * 
 * <p>
 * A lottery scheduler associates a number of tickets with each thread. When a
 * thread needs to be dequeued, a random lottery is held, among all the tickets
 * of all the threads waiting to be dequeued. The thread that holds the winning
 * ticket is chosen.
 * 
 * <p>
 * Note that a lottery scheduler must be able to handle a lot of tickets
 * (sometimes billions), so it is not acceptable to maintain state for every
 * ticket.
 * 
 * <p>
 * A lottery scheduler must partially solve the priority inversion problem; in
 * particular, tickets must be transferred through locks, and through joins.
 * Unlike a priority scheduler, these tickets add (as opposed to just taking the
 * maximum).
 */
public class LotteryScheduler extends Scheduler {
	/**
	 * Allocate a new lottery scheduler.
	 */
	public LotteryScheduler() {
	}

	/**
	 * Allocate a new lottery thread queue.
	 * 
	 * @param transferPriority <tt>true</tt> if this queue should transfer
	 * tickets from waiting threads to the owning thread.
	 * @return a new lottery thread queue.
	 */
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		// implement me
		LotteryQueue newQueue = new LotteryQueue(transferPriority);
		
		queueList.add(newQueue);
		
		return newQueue;
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
		
		int numberOfTickets = getNumberOfTicketsInSystem();
		
		int currentPriority = getThreadState(thread).priority;
		
		int difference = priority - currentPriority;
		
		if(difference + numberOfTickets <= priorityMaximum)
			getThreadState(thread).setPriority(priority);
	}
	
	public boolean increasePriority()
	{
		boolean intStatus = Machine.interrupt().disable();
		boolean ret = true;

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		
		int numberOfTickets = getNumberOfTicketsInSystem();
		
		if (priority == priorityMaximum || numberOfTickets >= priorityMaximum)
			ret = false;
		else
			setPriority(thread, priority + 1);

		Machine.interrupt().restore(intStatus);
		return ret;
	}
	
	public boolean decreasePriority()
	{
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
	
	private int getNumberOfTicketsInSystem()
	{
		int totalTickets = 0;
		
		for(LotteryQueue lQueue : queueList)
		{
			totalTickets += lQueue.numberOfTicketsInQueue;
		}
		
		return totalTickets;
	}
	
	public static final int priorityDefault = 1;
	
	public static final int priorityMinimum = 1;
	
	public static final int priorityMaximum = Integer.MAX_VALUE;
	
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
	
	protected class LotteryQueue extends ThreadQueue
	{
		public boolean transferPriority;
		
		LotteryQueue(boolean transferPriority)
		{
			this.transferPriority = transferPriority;
		}
		
		public void waitForAccess(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).waitForAccess(this);
			threadList.add(thread);
		}

		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).acquire(this);
		}

		public KThread nextThread() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me
			if(transferPriority)
				getThreadState(owner).release(this);
			
			if(threadList.isEmpty())
			{
				numberOfTicketsInQueue = 0;
				return null;
			}
			
			HashMap<Integer, KThread> ticketMap = new HashMap<Integer, KThread>();
			
			int noOfTicketsIssued = 0;
			
			for(KThread thread : threadList)
			{
				int noOfTicketsHeld = getThreadState(thread).getEffectivePriority();
				
				for(int i = 0; i < noOfTicketsHeld; i++)
				{
					ticketMap.put(noOfTicketsIssued, thread);
					noOfTicketsIssued++;
				}
			}
			
			if(noOfTicketsIssued > priorityMaximum)
				noOfTicketsIssued = priorityMaximum;
			
			numberOfTicketsInQueue = noOfTicketsIssued;
			
			int randomTicket = new Random().nextInt(noOfTicketsIssued);
			
			KThread nextThread = ticketMap.get(randomTicket); 
			
			threadList.remove(nextThread);
			
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
			if(threadList.isEmpty())
				return null;
			
			ThreadState nxtThrdState;
			
			nxtThrdState = getThreadState(threadList.peekFirst());
			return nxtThrdState;
		}

		public void print() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me (if you want)
		}
		
		private LinkedList<KThread> threadList = new LinkedList<KThread>();
		
		KThread owner;
		
		private int numberOfTicketsInQueue = 0;
	}
	
	protected class ThreadState
	{
		public ThreadState(KThread thread)
		{
			this.thread = thread;
			priority = priorityDefault;
		}
		
		public void waitForAccess(LotteryQueue lQueue)
		{	
			return;
		}
		
		public void acquire(LotteryQueue lQueue)
		{	
			if(lQueue.transferPriority)
			{
				acqQueueList.add(lQueue);
				lQueue.owner = this.thread;
			}
		}
		
		public int getPriority()
		{
			return priority;
		}
		
		public void setPriority(int priority)
		{	
			this.priority = priority;
		}
		
		private void release(LotteryQueue lQueue)
		{
			acqQueueList.remove(lQueue);
			
			lQueue.owner = null;
		}
		
		private int getEffectivePriority()
		{
			int noOfTicketsHeld = priority;
			
			for(LotteryQueue lQueue : acqQueueList)
			{
				for(KThread thread : lQueue.threadList)
				{
					ThreadState threadState = getThreadState(thread);
					noOfTicketsHeld += threadState.getEffectivePriority();
				}
			}
			
			return noOfTicketsHeld;
		}
		
		protected KThread thread;
		
		private HashSet<LotteryQueue> acqQueueList = new HashSet<LotteryQueue>();
		
		private int priority;
	}
	
	private ArrayList<LotteryQueue> queueList = new ArrayList<LotteryQueue>();
}
