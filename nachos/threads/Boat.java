package nachos.threads;

import nachos.ag.BoatGrader;
import nachos.machine.Lib;
import nachos.machine.Machine;

public class Boat {
	static BoatGrader bg;
	static private int test = 1;
	public static void selfTest() {
		BoatGrader b = new BoatGrader();
		
		
		System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
		begin(1, 2, b);
		
		test++;
		System.out.println("\n ***Testing Boats with only 2 children***");
		begin(6, 3, b);


		test++;
		System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
		begin(1, 2, b);

//		test++;
//		//for(int i=0;i<200000000;i++);
//		System.out.println("\n ***Testing Boats with 3 children, 3 adults***");
//		begin(3, 240, b);
//		//for(int i=0;i<200000000;i++);

		test++;
		System.out.println("\n ***Testing Boats with 3 children, 3 adults***");
		begin(6, 3, b);
	}

	public static void begin(int adults, int children, BoatGrader b) {
		// Store the externally generated autograder in a class
		// variable to be accessible by children.
		bg = b;

		// Instantiate global variables here
		// Create threads here. See section 3.4 of the Nachos for Java
		// Walkthrough linked from the projects page.
	    masterLock = new Lock();
		isBoatonO = new Condition(masterLock);
		isBoatonM = new Condition(masterLock);
		isOver 	  = new Condition(masterLock);
		waitForMChild =new Condition(masterLock);
		childrenfromOonM=0;
		waitForPassenger=new Condition(masterLock);
			
		childOnboard=false;
		boatOn='O';
		isOverB=false;
			
		totalAdults		=adults; 			//cannot be used by anybody except mai nthread
		totalChildren	=children; 			//cannot be used by anybody except mai nthread
		totalAdultsOnM  = 0;
		totalChildrenOnM =0; 
		totalAdultsOnO  = 0; 
		totalChildrenOnO= 0; 

		for(int i=0; i<adults; i++){
			Runnable r = new Runnable() {
				public void run() {
					AdultItinerary();
				}
			};
			KThread t = new KThread(r);
			t.setName(test + " Adult thread-"+i+"O");
			t.fork(); 	
		}

		for(int j=0; j<children; j++){
			Runnable r = new Runnable() {
				public void run() {
					ChildItinerary();
				}
			};
			KThread t = new KThread(r);
			t.setName(test + "Child Thread-"+j+"O");
			t.fork(); 
		}
		masterLock.acquire();
		while(!isOverB)
		{
			isOver.sleep();
			dispNum();	
			if(totalAdults==totalAdultsOnM  && totalChildren==totalChildrenOnM)
				isOverB=true;
		}
		
		//isBoatonO.wakeAll();
		if (!isOverB)
			isBoatonM.wakeAll();
		//Wait for all children to exit before terminating main thread
		isOver.sleep();
		masterLock.release();
				
		System.out.println("everyne has reached the M island.bye Byeeeee");
		
			
			
	}

	static void dispNum()
	{
		System.out.println("===========================================================================");
		System.out.println( "total children " + totalChildren);
		System.out.println( "total Adults " + totalAdults);
		System.out.println(  "total children On M" + totalChildrenOnM);
		System.out.println( "total adults on M " + totalAdultsOnM);
		System.out.println(  "total children On O" + totalChildrenOnO);
		System.out.println( "total adults on O " + totalAdultsOnO);
		System.out.println("boat on" +boatOn);
		
		System.out.println("===========================================================================");
		
	}
	static void AdultItinerary() {
		/*
		 * This is where you should put your solutions. Make calls to the
		 * BoatGrader to show that it is synchronized. For example:
		 * bg.AdultRowToMolokai(); indicates that an adult has rowed the boat
		 * across to Molokai
		 */
		masterLock.acquire();
		//initialisation...
		totalAdultsOnO++;
		dispNum();
		masterLock.release();
		KThread.currentThread().yield();
		while(1==1)
		{
			masterLock.acquire();
			
			if(!getNoOfchildrenfromOonM())
			{
				waitForMChild.sleep();
				masterLock.release();
				continue;	//goto wait on cildren on Molokai first
			}
						
			if(boatOn=='M')	
			{
			isBoatonO.sleep();  
			masterLock.release();
			continue;	//goto wait on cildren on Molokai first
			}
			// is boat on O
     		if(childOnboard)
			{
				masterLock.release();
				KThread.currentThread().yield();
				continue;	//goto wait on cildren on Molokai first
			}
	     	//if boat is not on M and no children are on it , then you can board it useless adult fellow.. poi thola...
			break;
		}
		totalAdultsOnO--;
		bg.AdultRowToMolokai();
		totalAdultsOnM++;
		boatOn='M';
		isBoatonM.wake();
		System.out.println("this Adult"+KThread.currentThread().getName()+" is useless after this and hence he is leaving the scene..bye Byeeeee");
		masterLock.release();
		//masterLock.dispLockHolder();
		System.out.println(KThread.currentThread().getName()+"is done....");
		KThread.finish();
	}

	static void ChildItinerary() {
		masterLock.acquire();
		//initialisation...
		totalChildrenOnO++;
		dispNum();
		masterLock.release();
		KThread.currentThread().yield();
		//when child leave s for M, you need to inremnet chld on M, and also do a waitForChildM wake to awkaen the adults waiting for the same..remembrer hat..
		
		char loc='O';
		
		while(!isOverB)
		{
			masterLock.acquire();
			if(boatOn=='O' && loc=='O')
			{
				if(childOnboard==true)			//passenger
				{
					bg.ChildRideToMolokai();
					waitForPassenger.wake();
					totalChildrenOnO--;
					totalChildrenOnM++;
					boatOn='M';
					loc='M';
					inctNoOfChildrenfromOonM();
					waitForMChild.wake();		//wake up one adult on O for each child arribed at M
					System.out.println("this Child"+KThread.currentThread().getName()+" is going to wait at M and board the boat back to O if nto finished");
					isOver.wake();
					isBoatonM.sleep();
					masterLock.release();
				}
				else 							/// no one on board .. so rider
				{	
					childOnboard=true;
					bg.ChildRowToMolokai();
					if(totalChildrenOnO > 1)
					{
						isBoatonO.wakeAll();
						System.out.println("this Child"+KThread.currentThread().getName()+" is waiting for pasenger");
						waitForPassenger.sleep();
					}
					
					totalChildrenOnO--;
					totalChildrenOnM++;
					loc='M';
					inctNoOfChildrenfromOonM();
					isBoatonM.wake();			//awaken only once
					waitForMChild.wake();
					System.out.println("this Child"+KThread.currentThread().getName()+" is going to wait at M and board the boat back to O if nto finished");
					childOnboard=false;
					isOver.wake();
					isBoatonM.sleep();
					masterLock.release();
				}
						
			}
			else if(boatOn=='M' && loc=='M')	//boat is on M
			{
				
				childOnboard=true;
				bg.ChildRowToOahu();
				totalChildrenOnM--;
				totalChildrenOnO++;
				loc='O';
				boatOn='O';
				dectNoOfChildrenfromOonM();
				isBoatonO.wakeAll();			//awaken only once
				System.out.println("this Child"+KThread.currentThread().getName()+" is now at O");
				childOnboard=false;
				isBoatonO.sleep();
				masterLock.release();
			}
			else if(boatOn=='M' && loc=='O')
				{
				isBoatonO.sleep();
				masterLock.release();
				}
			else if(boatOn=='O' && loc=='M')
			{
				isBoatonM.sleep();
				masterLock.release();
			}
		}
		//masterLock.release();
		//masterLock.dispLockHolder();
		System.out.println(KThread.currentThread().getName()+"is done....");
		//Test completed. Now wake up main thread
		masterLock.acquire();
		isOver.wake();
		masterLock.release();
		
		KThread.finish();
	}

	static void SampleItinerary() {
		// Please note that this isn't a valid solution (you can't fit
		// all of them on the boat). Please also note that you may not
		// have a single thread calculate a solution and then just play
		// it back at the autograder -- you will be caught.
		System.out.println("\n ***Everyone piles on the boat and goes to Molokai***");
		bg.AdultRowToMolokai();
		bg.ChildRideToMolokai();
		bg.AdultRideToMolokai();
		bg.ChildRideToMolokai();
	}

	static boolean getNoOfchildrenfromOonM()
	{
		return childrenfromOonM>0;
	}
	
	static void inctNoOfChildrenfromOonM()
	{
		childrenfromOonM++;		
	}
	
	static void dectNoOfChildrenfromOonM()
	{
		childrenfromOonM--;		
	}
	
	private static Lock masterLock;
	private static Condition isBoatonO;
	private static Condition isBoatonM;
	private static Condition isOver;
	private static Condition waitForMChild;
	private static int childrenfromOonM;
	private static Condition waitForPassenger;
	
	private static boolean childOnboard;
	private static char boatOn;
	private static boolean isOverB;
	private static int totalAdults; 
	private static int totalChildren; 
	private static int totalAdultsOnM; 
	private static int totalChildrenOnM; 
	private static int totalAdultsOnO; 
	private static int totalChildrenOnO; 
	
}
