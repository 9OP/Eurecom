package tests;

import java.util.concurrent.TimeUnit;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
// originally from : joins.C

import iterator.*;
import heap.*;
import global.*;
import index.*;
import java.io.*;
import java.util.*;
import javax.xml.namespace.QName;
import java.lang.*;
import diskmgr.*;
import bufmgr.*;
import btree.*;
import catalog.*;

/**
 * Here is the implementation for the tests. There are N tests performed. We start off by showing
 * that each operator works on its own. Then more complicated trees are constructed. As a nice
 * feature, we allow the user to specify a selection condition. We also allow the user to hardwire
 * trees together.
 */


// Define the R schema
class R {
  public int int1;
  public int int2;
  public int int3;
  public int int4;

  public R(int _int1, int _int2, int _int3, int _int4) {
    int1 = _int1;
    int2 = _int2;
    int3 = _int3;
    int4 = _int4;
  }

  public void show() {
    System.out.print(int1);
    System.out.print(int2);
    System.out.print(int3);
    System.out.print(int4);
  }
}


// Define the S schema
class S {
  public int int1;
  public int int2;
  public int int3;
  public int int4;

  public S(int _int1, int _int2, int _int3, int _int4) {
    int1 = _int1;
    int2 = _int2;
    int3 = _int3;
    int4 = _int4;
  }

  public void show() {
    System.out.print(int1);
    System.out.print(int2);
    System.out.print(int3);
    System.out.print(int4);
  }
}


// Define the R schema
class Q {
  public int int1;
  public int int2;
  public int int3;
  public int int4;

  public Q(int _int1, int _int2, int _int3, int _int4) {
    int1 = _int1;
    int2 = _int2;
    int3 = _int3;
    int4 = _int4;
  }
}


class JoinsDriver implements GlobalConst {

  private boolean OK = true;
  private boolean FAIL = false;
  private Vector s;
  private Vector r;
  private Vector q;
  public String pathToData = new File("").getAbsolutePath();

  public void populateData(String pathtodata, String filename, Vector table, Integer maxRows) {
    BufferedReader reader;
    boolean isR = filename.split("\\.")[0].equals("R");
    boolean isS = filename.split("\\.")[0].equals("S");
    boolean isQ = filename.split("\\.")[0].equals("Q");
    Integer counter = 0;
    try {
      reader = new BufferedReader(
          new FileReader(pathToData + "/../../QueriesData_newvalues/" + filename));
      String line = reader.readLine();
      if (line != null) {
        line = reader.readLine(); // don't parse the headers
      }
      while (line != null && counter < maxRows) {
        counter += 1;
        String[] tableAttrs = line.trim().split(",");
        int[] parsedAttrs = new int[tableAttrs.length];
        for (int i = 0; i < tableAttrs.length; ++i) {
          parsedAttrs[i] = Integer.parseInt(tableAttrs[i]);
        }
        if (isR) {
          table.addElement(new R(parsedAttrs[0], parsedAttrs[1], parsedAttrs[2], parsedAttrs[3]));
        } else if (isS) {
          table.addElement(new S(parsedAttrs[0], parsedAttrs[1], parsedAttrs[2], parsedAttrs[3]));
        } else if (isQ) {
          table.addElement(new Q(parsedAttrs[0], parsedAttrs[1], parsedAttrs[2], parsedAttrs[3]));
        }
        // read next line
        line = reader.readLine();
      }
      reader.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Constructor
   */
  public JoinsDriver(Integer maxRows) {
    // build R, S, Q tables
    r = new Vector();
    s = new Vector();
    q = new Vector();

    populateData(pathToData, "S.txt", s, maxRows);
    populateData(pathToData, "R.txt", r, maxRows);
    populateData(pathToData, "Q.txt", q, maxRows);

    boolean status = OK;
    int numS = s.size(); // number of entry in s
    int numS_attrs = 4;
    int numR = r.size(); // number of entry in r
    int numR_attrs = 4;
    int numQ = q.size(); // number of entry in s
    int numQ_attrs = 4;

    String dbpath = "/tmp/" + System.getProperty("user.name") + ".minibase.jointest2db";
    String logpath = "/tmp/" + System.getProperty("user.name") + ".joinlog2";

    String remove_cmd = "/bin/rm -rf ";
    String remove_logcmd = remove_cmd + logpath;
    String remove_dbcmd = remove_cmd + dbpath;
    String remove_joincmd = remove_cmd + dbpath;

    try {
      Runtime.getRuntime().exec(remove_logcmd);
      Runtime.getRuntime().exec(remove_dbcmd);
      Runtime.getRuntime().exec(remove_joincmd);
    } catch (IOException e) {
      System.err.println("" + e);
    }

    /*
     * ExtendedSystemDefs extSysDef = new ExtendedSystemDefs( "/tmp/minibase.jointestdb",
     * "/tmp/joinlog", 1000,500,200,"Clock");
     */

    SystemDefs sysdef = new SystemDefs(dbpath, 1000, NUMBUF, "Clock");


    // creating the S relation
    AttrType[] Stypes = new AttrType[4];
    Stypes[0] = new AttrType(AttrType.attrInteger);
    Stypes[1] = new AttrType(AttrType.attrInteger);
    Stypes[2] = new AttrType(AttrType.attrInteger);
    Stypes[3] = new AttrType(AttrType.attrInteger);

    // SOS
    short[] Ssizes = new short[1];
    Ssizes[0] = 30; // first elt. is 30

    Tuple t = new Tuple();
    try {
      t.setHdr((short) 4, Stypes, Ssizes);
    } catch (Exception e) {
      System.err.println("*** error in Tuple.setHdr() ***");
      status = FAIL;
      e.printStackTrace();
    }

    int size = t.size();

    // inserting the tuple into file "S"
    RID rid;
    Heapfile f = null;
    try {
      f = new Heapfile("S.in");
    } catch (Exception e) {
      System.err.println("*** error in Heapfile constructor ***");
      status = FAIL;
      e.printStackTrace();
    }

    t = new Tuple(size);
    try {
      t.setHdr((short) 4, Stypes, Ssizes);
    } catch (Exception e) {
      System.err.println("*** error in Tuple.setHdr() ***");
      status = FAIL;
      e.printStackTrace();
    }

    for (int i = 0; i < numS; i++) {
      try {
        t.setIntFld(1, ((S) s.elementAt(i)).int1);
        t.setIntFld(2, ((S) s.elementAt(i)).int2);
        t.setIntFld(3, ((S) s.elementAt(i)).int3);
        t.setIntFld(4, ((S) s.elementAt(i)).int4);
      } catch (Exception e) {
        System.err.println("*** Heapfile error in Tuple.setStrFld() ***");
        status = FAIL;
        e.printStackTrace();
      }

      try {
        rid = f.insertRecord(t.returnTupleByteArray());
      } catch (Exception e) {
        System.err.println("*** error in Heapfile.insertRecord() ***");
        status = FAIL;
        e.printStackTrace();
      }
    }
    if (status != OK) {
      // bail out
      System.err.println("*** Error creating relation for S");
      Runtime.getRuntime().exit(1);
    }

    // creating the R relation
    AttrType[] Rtypes = new AttrType[4];
    Rtypes[0] = new AttrType(AttrType.attrInteger);
    Rtypes[1] = new AttrType(AttrType.attrInteger);
    Rtypes[2] = new AttrType(AttrType.attrInteger);
    Rtypes[3] = new AttrType(AttrType.attrInteger);

    short[] Rsizes = new short[1];
    Rsizes[0] = 30;
    t = new Tuple();
    try {
      t.setHdr((short) 4, Rtypes, Rsizes);
    } catch (Exception e) {
      System.err.println("*** error in Tuple.setHdr() ***");
      status = FAIL;
      e.printStackTrace();
    }

    size = t.size();

    // inserting the tuple into file "R"
    f = null;
    try {
      f = new Heapfile("R.in");
    } catch (Exception e) {
      System.err.println("*** error in Heapfile constructor ***");
      status = FAIL;
      e.printStackTrace();
    }

    t = new Tuple(size);
    try {
      t.setHdr((short) 4, Rtypes, Rsizes);
    } catch (Exception e) {
      System.err.println("*** error in Tuple.setHdr() ***");
      status = FAIL;
      e.printStackTrace();
    }

    for (int i = 0; i < numR; i++) {
      try {
        t.setIntFld(1, ((R) r.elementAt(i)).int1);
        t.setIntFld(2, ((R) r.elementAt(i)).int2);
        t.setIntFld(3, ((R) r.elementAt(i)).int3);
        t.setIntFld(4, ((R) r.elementAt(i)).int4);

      } catch (Exception e) {
        System.err.println("*** error in Tuple.setStrFld() ***");
        status = FAIL;
        e.printStackTrace();
      }

      try {
        rid = f.insertRecord(t.returnTupleByteArray());
      } catch (Exception e) {
        System.err.println("*** error in Heapfile.insertRecord() ***");
        status = FAIL;
        e.printStackTrace();
      }
    }
    if (status != OK) {
      // bail out
      System.err.println("*** Error creating relation for R");
      Runtime.getRuntime().exit(1);
    }

    // creating the Q relation
    AttrType[] Qtypes = new AttrType[4];
    Qtypes[0] = new AttrType(AttrType.attrInteger);
    Qtypes[1] = new AttrType(AttrType.attrInteger);
    Qtypes[2] = new AttrType(AttrType.attrInteger);
    Qtypes[3] = new AttrType(AttrType.attrInteger);

    short[] Qsizes = new short[1];
    Qsizes[0] = 30;
    t = new Tuple();
    try {
      t.setHdr((short) 4, Qtypes, Qsizes);
    } catch (Exception e) {
      System.err.println("*** error in Tuple.setHdr() ***");
      status = FAIL;
      e.printStackTrace();
    }

    size = t.size();

    // inserting the tuple into file "Q"
    f = null;
    try {
      f = new Heapfile("Q.in");
    } catch (Exception e) {
      System.err.println("*** error in Heapfile constructor ***");
      status = FAIL;
      e.printStackTrace();
    }

    t = new Tuple(size);
    try {
      t.setHdr((short) 4, Qtypes, Qsizes);
    } catch (Exception e) {
      System.err.println("*** error in Tuple.setHdr() ***");
      status = FAIL;
      e.printStackTrace();
    }

    for (int i = 0; i < numQ; i++) {
      try {
        t.setIntFld(1, ((Q) q.elementAt(i)).int1);
        t.setIntFld(2, ((Q) q.elementAt(i)).int2);
        t.setIntFld(3, ((Q) q.elementAt(i)).int3);
        t.setIntFld(4, ((Q) q.elementAt(i)).int4);

      } catch (Exception e) {
        System.err.println("*** error in Tuple.setStrFld() ***");
        status = FAIL;
        e.printStackTrace();
      }

      try {
        rid = f.insertRecord(t.returnTupleByteArray());
      } catch (Exception e) {
        System.err.println("*** error in Heapfile.insertRecord() ***");
        status = FAIL;
        e.printStackTrace();
      }
    }
    if (status != OK) {
      // bail out
      System.err.println("*** Error creating relation for Q");
      Runtime.getRuntime().exit(1);
    }
  }

  public boolean runTests() {
    try {
      Query("/../../QueriesData_newvalues/query_1a.txt", "NLJ"); // Single predicate query 1a NLJ
      Query("/../../QueriesData_newvalues/query_1b.txt", "NLJ"); // Double predicate query 1b NLJ
      Query("/../../QueriesData_newvalues/query_2a.txt", "NLJ"); // Single predicate query 2a NLJ
      Query("/../../QueriesData_newvalues/query_2a.txt", "IEJ_2a"); // Single predicate query 2a IEJoin
      Query("/../../QueriesData_newvalues/query_2b.txt", "NLJ"); // Double predicate query 2b NLJ
      Query("/../../QueriesData_newvalues/query_2b.txt", "IEJ_2b"); // Double predicate query 2b IEJoin
    } catch (FileNotFoundException ex) {
      ex.printStackTrace();
    } catch (IOException ex) {
      ex.printStackTrace();
    }

    System.out.print("Finished joins testing" + "\n");

    return true;
  }

  public void CondExpr(CondExpr[] expr, String query_path, Integer[] selectCols,
      String[] relations) {
    // LINE 1: Rel1 col# Rel2 col#
    // LINE 2: Rel1 Rel2
    // LINE 3: Rel1 col# op 1 Rel2 col#
    // LINE 4: AND/OR
    // LINE 5: Rel1 col# op 2 Rel2 col#
    String selectRel1 = "", selectRel2 = "";
    Integer selectRel1Col = 0, selectRel2Col = 0;
    String rel1 = "", rel2 = "";
    String whereRel1_1 = "", whereRel2_1 = "", whereRel1_2 = "", whereRel2_2 = "";
    Integer whereRel1Col_1 = 0, whereRel2Col_1 = 0, whereRel1Col_2 = 0, whereRel2Col_2 = 0;
    Integer op1 = 0, op2 = 0;
    String interPredicate = "";
    String[] op_string = {"=", "<", ">", "!=", "<=", ">="};
    Boolean doublePredicate = false;
    
    try {
      BufferedReader query = new BufferedReader(new FileReader(pathToData + query_path));
      // Line1
      String[] line1 = query.readLine().split(" ");
      selectRel1 = line1[0].split("_")[0];
      selectRel2 = line1[1].split("_")[0];
      selectRel1Col = Integer.parseInt(line1[0].split("_")[1]);
      selectRel2Col = Integer.parseInt(line1[1].split("_")[1]);
      // Line2
      String[] line2 = query.readLine().split(" ");
      rel1 = line2[0];
      if (line2.length < 2) {
        rel2 = rel1;
      } else {
        rel2 = line2[1];
      }
      // Line3: 1st predicate
      String[] line3 = query.readLine().split(" ");
      op1 = Integer.parseInt(line3[1]);
      whereRel1_1 = line3[0].split("_")[0];
      whereRel2_1 = line3[2].split("_")[0];
      whereRel1Col_1 = Integer.parseInt(line3[0].split("_")[1]);
      whereRel2Col_1 = Integer.parseInt(line3[2].split("_")[1]);
      // Line4
      String line4 = query.readLine();
      if (line4 != null) {
        doublePredicate = true;
        interPredicate = line4;
        // Line5: 2nd predicate
        String[] line5 = query.readLine().split(" ");
        op2 = Integer.parseInt(line5[1]);
        whereRel1_2 = line5[0].split("_")[0];
        whereRel2_2 = line5[2].split("_")[0];
        whereRel1Col_2 = Integer.parseInt(line5[0].split("_")[1]);
        whereRel2Col_2 = Integer.parseInt(line5[2].split("_")[1]);
      }
      query.close();
    } catch (FileNotFoundException ex) {
      ex.printStackTrace();
    } catch (IOException ex) {
      ex.printStackTrace();
    }

    System.out.print("  SELECT   " + selectRel1 + "." + selectRel1Col + " " + selectRel2 + "."
        + selectRel2Col + "\n" + "  FROM     " + rel1 + " " + rel2 + "\n" + "  WHERE    "
        + whereRel1_1 + "." + whereRel1Col_1 + " " + op_string[op1] + " " + whereRel2_1 + "."
        + whereRel2Col_1 + " ");
    if (doublePredicate) {
      System.out.print(interPredicate + " " + whereRel1_2 + "." + whereRel1Col_2 + " "
          + op_string[op2] + " " + whereRel2_2 + "." + whereRel2Col_2);
    }
    System.out.print("\nPlan used:\n" + "  Pi(" + selectRel1 + "." + selectRel1Col + ", "
        + selectRel2 + "." + selectRel2Col + ") (" + rel1 + " |><| " + rel2 + "))\n\n");

    expr[0] = new CondExpr();
    expr[0].next = null;
    expr[0].op = new AttrOperator(op1);
    expr[0].type1 = new AttrType(AttrType.attrSymbol);
    expr[0].type2 = new AttrType(AttrType.attrSymbol);
    expr[0].operand1.symbol = new FldSpec(new RelSpec(RelSpec.innerRel), whereRel1Col_1);
    expr[0].operand2.symbol = new FldSpec(new RelSpec(RelSpec.outer), whereRel2Col_1);

    if (doublePredicate) {
      expr[1] = new CondExpr();
      expr[1].next = null;
      expr[1].op = new AttrOperator(op2);
      expr[1].type1 = new AttrType(AttrType.attrSymbol);
      expr[1].type2 = new AttrType(AttrType.attrSymbol);
      expr[1].operand1.symbol = new FldSpec(new RelSpec(RelSpec.innerRel), whereRel1Col_2);
      expr[1].operand2.symbol = new FldSpec(new RelSpec(RelSpec.outer), whereRel2Col_2);
      expr[2] = null;
    } else {
      expr[1] = null;
    }

    selectCols[0] = selectRel1Col;
    selectCols[1] = selectRel2Col;
    relations[0] = new String(rel1); // inner
    relations[1] = new String(rel2); // outer
  }

  public void Query(String query_path, String join_type) throws FileNotFoundException, IOException {
    long startTime = System.nanoTime();
    System.out.println("\n\n******** " + query_path + " ********");
    if (join_type.equals("NLJ")) {
      System.out.println(">>> Nested Loop Join\n");
    } else {
      System.out.println(">>> IE Join\n");
    }
    boolean status = OK;
    CondExpr[] outFilter = new CondExpr[3];
    Integer[] select_cols = new Integer[2];
    String[] relations = new String[2]; // innerRelation, outerRelation
    CondExpr(outFilter, query_path, select_cols, relations);
    String innerRelation = new String(relations[0]);
    String outerRelation = new String(relations[1]);

    Tuple t = new Tuple();
    t = null;

    AttrType[] Stypes = {new AttrType(AttrType.attrInteger), new AttrType(AttrType.attrInteger),
        new AttrType(AttrType.attrInteger), new AttrType(AttrType.attrInteger)};

    AttrType[] Rtypes = {new AttrType(AttrType.attrInteger), new AttrType(AttrType.attrInteger),
        new AttrType(AttrType.attrInteger), new AttrType(AttrType.attrInteger)};

    AttrType[] Jtypes = {new AttrType(AttrType.attrInteger), new AttrType(AttrType.attrInteger)};

    FldSpec[] proj = {new FldSpec(new RelSpec(RelSpec.innerRel), select_cols[1]), // R
        new FldSpec(new RelSpec(RelSpec.outer), select_cols[0]),}; // S

    FldSpec[] Sprojection = {
        new FldSpec(new RelSpec(RelSpec.outer), 1),
        new FldSpec(new RelSpec(RelSpec.outer), 2), 
        new FldSpec(new RelSpec(RelSpec.outer), 3),
        new FldSpec(new RelSpec(RelSpec.outer), 4),
      };

    FileScan am = null;
    try {
      am = new FileScan(outerRelation+".in", Stypes, null, (short) 4, (short) 4, Sprojection, null);
    } catch (Exception e) {
      status = FAIL;
      System.err.println("" + e);
    }

    NestedLoopsJoins nlj = null;
    IEJoin_2a iej_2a = null;
    IEJoin_2b iej_2b = null;

    try {
      if (join_type.equals("NLJ")) {
        nlj = new NestedLoopsJoins(Stypes, 4, null, Rtypes, 4, null, 10, am, innerRelation+".in", outFilter, null, proj, 2);
      }
      if (join_type.equals("IEJ_2a")) {
        iej_2a = new IEJoin_2a(Stypes, 4, null, 10, am, innerRelation + ".in", outFilter, proj, 2);
      }
      if (join_type.equals("IEJ_2b")) {
        iej_2b = new IEJoin_2b(Stypes, 4, null, 10, am, innerRelation + ".in", outFilter, proj, 2);
      }
    } catch (Exception e) {
      System.err.println("*** Error preparing for nested_loop_join");
      System.err.println("" + e);
      e.printStackTrace();
      Runtime.getRuntime().exit(1);
    }

    t = null;
    try {
      if (join_type.equals("NLJ")) {
        while ((t = nlj.get_next()) != null) {
          ; //t.print(Jtypes);
        }
      } else if (join_type.equals("IEJ_2a")) {
        while ((t = iej_2a.get_next()) != null) {
          ; //t.print(Jtypes);
        }
      } else if (join_type.equals("IEJ_2b")) {
        while ((t = iej_2b.get_next()) != null) {
          ; //t.print(Jtypes);
        }
      }
    } catch (Exception e) {
      System.err.println("" + e);
      e.printStackTrace();
      Runtime.getRuntime().exit(1);
    }

    long endTime = System.nanoTime();
    long elapsedTime = (endTime - startTime);
    System.out.println("Execution time in us: " + elapsedTime / 1000 + "\n");
  }
}


public class JoinTest {
  public static void main(String argv[]) {
    boolean sortstatus;
    // SystemDefs global = new SystemDefs("bingjiedb", 100, 70, null);
    // JavabaseDB.openDB("/tmp/nwangdb", 5000);

    Integer maxRows = 1500;
    JoinsDriver jjoin = new JoinsDriver(maxRows);
    System.out.print("JoinTest start... \nRows loaded per relation R, S, Q: ");
    System.out.println(maxRows + "\n");
    sortstatus = jjoin.runTests();
    if (sortstatus != true) {
      System.out.println("Error ocurred during join tests");
    } else {
      System.out.println("join tests completed successfully");
    }
  }
}

