/*  1:   */ package nachos.ag;
/*  2:   */ 
/*  3:   */ import nachos.machine.Lib;
/*  4:   */ import nachos.threads.KThread;
/*  5:   */ 
/*  6:   */ public class Join
/*  7:   */   extends ThreadGrader
/*  8:   */ {
/*  9:   */   void run()
/* 10:   */   {
/* 11:26 */     super.run();
/* 12:   */     
/* 13:28 */     boolean joinAfterFinish = getBooleanArgument("joinAfterFinish");
/* 14:   */     
/* 15:30 */     ThreadGrader.ThreadExtension t = jfork(new Runnable()
/* 16:   */     {
/* 17:   */       public void run() {}
/* 18:   */     });
/* 19:32 */     if (joinAfterFinish) {
/* 20:33 */       j(t);
/* 21:   */     }
/* 22:35 */     t.thread.join();
/* 23:   */     
/* 24:37 */     Lib.assertTrue(t.finished, "join() returned but target thread is not finished");
/* 25:   */     
/* 26:   */ 
/* 27:40 */     done();
/* 28:   */   }
/* 29:   */ }
