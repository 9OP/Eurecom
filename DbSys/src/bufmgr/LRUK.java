/* File LRUK.java */

package bufmgr;

import diskmgr.*;
import global.*;
import java.lang.*;
import java.util.*;

import java.util.HashMap;
import java.util.ArrayList;

public class LRUK extends Replacer {

  private int frames[];
  private int nframes;

  private int K;
  public HashMap<Integer, ArrayList<Long>> HIST;
  public HashMap<Integer, Long> LAST;
  private long CorellatedRefPeriod = 0;

  public Long get_k_access(int p, int k) {
    return HIST.get(p).get(k);
  }

  private void allocateHistBlock(int p) {
    ArrayList<Long> tmp = new ArrayList<Long>();
    for (int i = 0; i < K; i++)
      tmp.add(0L);
    HIST.put(p, tmp);
  }

  public int pageid;
  public int page_in_buffer;

  /**
   * This pushes the given frame to the end of the list.
   * 
   * @param frameNo the frame number
   */
  private void update(int frameNo) {
    ArrayList<Long> history;

    if (page_in_buffer == 0) {
      // pageid not in buffer -> allocate memory
      if (HIST.containsKey(pageid)) {
        // pageid in history -> update access
        history = HIST.get(pageid);
        for (int i = 1; i < K; i++) 
          history.set(i, history.get(i - 1));
        HIST.put(pageid, history);

      } else {
        allocateHistBlock(pageid);
      }
      // update last access 
      LAST.put(pageid, System.currentTimeMillis());
      history = HIST.get(pageid);
      history.set(0, System.currentTimeMillis());
      HIST.put(pageid, history);
      
    }

    else {
      // pageid is in buffer -> update access history
      Long t = System.currentTimeMillis();
      int p = frames[frameNo];
      Long correl_period_of_refd_page = 0L;
      if ((t - LAST.get(p) >= CorellatedRefPeriod)) {
        // new uncorrelated reference
        correl_period_of_refd_page = LAST.get(p) - HIST.get(p).get(0);
        history = HIST.get(pageid);
        for (int i = 1; i < K; i++) // shift history on the right to add a new access time
          history.set(i, history.get(i - 1) + correl_period_of_refd_page);
        history.set(0, System.currentTimeMillis()); // add new access time
        HIST.put(pageid, history); // commit history
        LAST.put(p, System.currentTimeMillis()); // new access time became last access time
      } else {
        // update last access to page p
        LAST.put(p, System.currentTimeMillis());
      }

    }
  }

  /**
   * Calling super class the same method Initializing the frames[] with number of buffer allocated
   * by buffer manager set number of frame used to zero
   *
   * @param mgr a BufMgr object
   * @see BufMgr
   * @see Replacer
   */
  public void setBufferManager(BufMgr mgr) {
    super.setBufferManager(mgr);
    frames = new int[mgr.getNumBuffers()];
    nframes = 0;

  }

  /* public methods */

  /**
   * Class constructor Initializing frames[] pointer = null.
   */
  public LRUK(BufMgr mgrArg, int k) {
    super(mgrArg);
    frames = null;
    pageid = -1;
    page_in_buffer = 0;
    K = k;

    HIST = new HashMap<Integer, ArrayList<Long>>();
    LAST = new HashMap<Integer, Long>();
  }

  /**
   * calll super class the same method pin the page in the given frame number move the page to the
   * end of list
   *
   * @param frameNo the frame number to pin
   * @exception InvalidFrameNumberException
   */
  public void pin(int frameNo) throws InvalidFrameNumberException {
    super.pin(frameNo);
    update(frameNo);

  }

  /**
   * Finding a free frame in the buffer pool or choosing a page to replace using LRUK policy
   *
   * @return return the frame number throws BufferPoolExceededException if failed
   */
  public int pick_victim() throws BufferPoolExceededException {
    int numBuffers = mgr.getNumBuffers();
    int frame = -1;

    if (nframes < numBuffers) {
      // buffer is not full
      frame = nframes++;
      frames[frame] = pageid;
      state_bit[frame].state = Pinned;
      (mgr.frameTable())[frame].pin();
      // generate or update history for the page
      update(pageid);
      return frame;
    }
    // buffer is full
    Long min;
    Long t = System.currentTimeMillis();
    min = t;
    int q;
    for (int i = 0; i < numBuffers; ++i) {
      q = frames[i];
      if (state_bit[i].state != Pinned) {
        // find page with oldest Kth ref and passed correlated ref period
        if ((t - LAST.get(q)) >= CorellatedRefPeriod && HIST.get(q).get(K-1) <= min) {
          frame = i;
          min = HIST.get(q).get(K-1);
        }
      }
      if (frame >= 0) {
        state_bit[frame].state = Pinned;
        (mgr.frameTable())[frame].pin();
        // generate or update history for the page
        update(frame);
        return frame;
      }
    }

    throw new BufferPoolExceededException(null, "BUFMGR: BUFFER_EXCEEDED.");
  }

  /**
   * get the page replacement policy name
   *
   * @return return the name of replacement policy used
   */
  public String name() {
    return "LRUK";
  }

  /**
   * print out the information of frame usage
   */
  public void info() {
    super.info();

    System.out.print("LRUK REPLACEMENT");

    for (int i = 0; i < nframes; i++) {
      if (i % 5 == 0)
        System.out.println();
      System.out.print("\t" + frames[i]);

    }
    System.out.println();
  }

  /*
   * returns the frames in the buffer pool
   */
  public int[] getFrames() {
    return frames;
  }
}
