package nachos.threads;

import nachos.machine.*;

/**
 * A <tt>Lock</tt> is a synchronization primitive that has two states,
 * <i>busy</i> and <i>free</i>. There are only two operations allowed on a lock:
 * 
 * <ul>
 * <li><tt>acquire()</tt>: atomically wait until the lock is <i>free</i> and
 * then set it to <i>busy</i>.
 * <li><tt>release()</tt>: set the lock to be <i>free</i>, waking up one waiting
 * thread if possible.
 * </ul>
 * 
 * <p>
 * Also, only the thread that acquired a lock may release it. As with
 * semaphores, the API does not allow you to read the lock state (because the
 * value could change immediately after you read it).
 */
public class Lock {
	/**
	 * Allocate a new lock. The lock will initially be <i>free</i>.
	 */
	public Lock() {
	}

	/**
	 * Atomically acquire this lock. The current thread must not already hold
	 * this lock.
	 */
	public void acquire() {
		Lib.assertTrue(!isHeldByCurrentThread());

		boolean intStatus = Machine.interrupt().disable();
		KThread thread = KThread.currentThread();

		if (lockHolder != null) {
			waitQueue.waitForAccess(thread);
			KThread.sleep();
		}
		else {
			waitQueue.acquire(thread);
			lockHolder = thread;
		}

		Lib.assertTrue(lockHolder == thread);
	//	System.out.println("Lock acquired by"+lockHolder.getName());
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Atomically release this lock, allowing other threads to acquire it.
	 */
	public void release() {
//		if(lockHolder == null)
//			System.out.println("no one is holdin g master lock but release is stil being attempted");
//		else
//			System.out.println(lockHolder.getName());
		
		Lib.assertTrue(isHeldByCurrentThread());

		boolean intStatus = Machine.interrupt().disable();
	//	System.out.println("Lock released by"+lockHolder.getName());
		if ((lockHolder = waitQueue.nextThread()) != null)
			lockHolder.ready();

		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Test if the current thread holds this lock.
	 * 
	 * @return true if the current thread holds this lock.
	 */
	public boolean isHeldByCurrentThread() {
		return (lockHolder == KThread.currentThread());
	}

//	public void dispLockHolder() {
//		
//		if(lockHolder == null)
//			System.out.println("no one is holdin g master lock");
//		else
//			System.out.println(lockHolder.getName());
//		
//		
//	}
	private KThread lockHolder = null;

	private ThreadQueue waitQueue = ThreadedKernel.scheduler
			.newThreadQueue(true);
}
