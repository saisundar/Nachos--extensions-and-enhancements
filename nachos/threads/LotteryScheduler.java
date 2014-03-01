package nachos.threads;

import nachos.machine.*;

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
public class LotteryScheduler {
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
		return new LotteryQueue(transferPriority);
	}
	
	public int getPriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getPriority();
	}
	
	public void setPriority(KThread thread, int priority) {
		Lib.assertTrue(Machine.interrupt().disabled());

		Lib.assertTrue(priority >= priorityMinimum
				&& priority <= priorityMaximum);

		getThreadState(thread).setPriority(priority);
	}
	
	public boolean increasePriority()
	{
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
		}

		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).acquire(this);
			owner = thread;
		}

		public KThread nextThread() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me
			getThreadState(owner).release(this);
			
			HashSet<Integer> allTickets = new HashSet<Integer>();
			
			HashMap<Integer, KThread> ticketMap = new HashMap<Integer, KThread>();
			
			for(KThread thread : threadList)
			{
				HashSet<Integer> ownedTickets = getThreadState(thread).getOwnedTickets();
				
				for(int ticket : ownedTickets)
				{
					ticketMap.put(ticket, thread);
				}
				
				allTickets.addAll(ownedTickets);
			}
			
			int randomTicket = getRandomTicket(allTickets);
			
			KThread nextThread = ticketMap.get(randomTicket); 
			
			threadList.remove(nextThread);
			
			getThreadState(nextThread).acquire(this);
			
			return nextThread;
		}
		
		/* returns a random ticket from a set of tickets*/
		private int getRandomTicket(HashSet<Integer> tickets)
		{
			int noOfTickets = tickets.size();
			int item = new Random().nextInt(noOfTickets);
			int i = 0;
			int chosenTicket = 0;
			
			for(int ticket : tickets)
			{
				if(i == item)
				{
					chosenTicket = ticket;
					break;
				}
				
				i++;
			}
			
			return chosenTicket;
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
	}
	
	protected class ThreadState
	{
		public ThreadState(KThread thread)
		{
			this.thread = thread;
			ownedTickets.add(ticketNo++);
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
			}
		}
		
		public HashSet<Integer> getOwnedTickets()
		{
			HashSet<Integer> acquiredTickets = new HashSet<Integer>();
			
			acquiredTickets.addAll(ownedTickets);

			/* get all tickets owned through donation*/
			for(LotteryQueue acqQueue : acqQueueList)
			{
				for(KThread thread : acqQueue.threadList)
				{
					acquiredTickets.addAll(getThreadState(thread).getOwnedTickets());
				}
			}
			
			return acquiredTickets;
		}
		
		public int getPriority()
		{
			return priority;
		}
		
		public void setPriority(int priority)
		{
			int oldPriority = this.priority;
			
			if(oldPriority > priority)
			{
				/*remove tickets*/
				int removeCount = oldPriority - priority;
				
				for(int ticket : ownedTickets)
				{
					if(removeCount == 0)
						break;
					ownedTickets.remove(ticket);
					removeCount--;
				}
			}
			else if(oldPriority < priority)
			{
				/*add tickets*/
				int addCount = priority - oldPriority;
				
				for(;addCount >= 0; addCount--)
					ownedTickets.add(ticketNo++);
			}
			
			this.priority = priority;
		}
		
		private void release(LotteryQueue lQueue)
		{
			if(lQueue.transferPriority)
				acqQueueList.remove(lQueue);
		}
		
		protected HashSet<Integer> ownedTickets = new HashSet<Integer>();
		
		protected KThread thread;
		
		private HashSet<LotteryQueue> acqQueueList = new HashSet<LotteryQueue>();
		
		private int priority;
	}
	
	private static int ticketNo = 0;
}
