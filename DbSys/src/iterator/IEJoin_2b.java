package iterator;


import heap.*;
import global.*;
import bufmgr.*;
import diskmgr.*;
import index.*;
import java.lang.*;
import java.io.*;
import java.util.*;

/**
 *
 * This file contains an implementation of the IE Join algorithm as described in the VLDBJ paper.
 */

public class IEJoin_2b extends Iterator {
    private AttrType _in1[];
    int j;
    int i = 0;
    private int in1_len;
    private Sort outer;
    private Sort inner;
    private Iterator _am;
    private TupleOrder[] order;
    private short t1_str_sizescopy[];
    private CondExpr OutputFilter[];
    private int n_buf_pgs; // # of buffer pages available.
    private boolean done, // Is the join complete
            get_from_outer; // if TRUE, a tuple is got from outer
    private Tuple inner_tuple;
    private Tuple Jtuple; // Joined tuple
    private FldSpec perm_mat[];
    private int nOutFlds;
    private Heapfile hf;
    private ArrayList<Tuple> L1;
    private ArrayList<Tuple> L2;
    private int[] P;
    private boolean[] B;
    private boolean[] eqOff_arr;
    private boolean eqOff;
    int n;

    /**
     * constructor Initialize the two relations which are joined, including relation type,
     * 
     * @param in1          Array containing field types of R.
     * @param len_in1      # of columns in R.
     * @param t1_str_sizes shows the length of the string fields.
     * @param amt_of_mem   IN PAGES
     * @param am           access method for left i/p to join
     * @param relationName access hfapfile for right i/p to join
     * @param outFilter    select expressions
     * @param proj_list    shows what input fields go where in the output tuple
     * @param n_out_flds   number of outer relation fileds
     * @exception IOException         some I/O fault
     * @exception NestedLoopException exception from this class
     */
    public IEJoin_2b(AttrType in1[], int len_in1, short t1_str_sizes[], int amt_of_mem, Iterator am,
            String relationName, CondExpr outFilter[], FldSpec proj_list[], int n_out_flds)
            throws IOException, NestedLoopException {

        _in1 = new AttrType[in1.length];
        L1 = new ArrayList<>();
        L2 = new ArrayList<>();
        System.arraycopy(in1, 0, _in1, 0, in1.length);
        in1_len = len_in1;
        inner_tuple = new Tuple();
        Jtuple = new Tuple();
        t1_str_sizescopy = t1_str_sizes;
        _am = am;

        OutputFilter = outFilter;
        n_buf_pgs = amt_of_mem;
        inner = null;
        done = false;
        get_from_outer = true;
        order = new TupleOrder[2];

        AttrType[] Jtypes = new AttrType[n_out_flds];
        short[] t_size;

        perm_mat = proj_list;
        nOutFlds = n_out_flds;
        eqOff_arr = new boolean[2];

        for (int i = 0; i < 2; ++i) {
            if (outFilter[i].op.attrOperator == AttrOperator.aopGT
                    || outFilter[i].op.attrOperator == AttrOperator.aopGE) {
                order[i] = new TupleOrder(TupleOrder.Ascending);
            } else {
                order[i] = new TupleOrder(TupleOrder.Descending);
            }

            // initialize eq off
            if (outFilter[i].op.attrOperator == AttrOperator.aopGE ||  outFilter[i].op.attrOperator == AttrOperator.aopLE) {
                eqOff_arr[i] = true;
            } else {
                eqOff_arr[i] = false;
            }
        }

        eqOff = !(eqOff_arr[0] && eqOff_arr[1]);

        // Sort differently depending on the operator

        try {
            // sort the tuples on the attribute we are considering for the where clause

            Sort sort_object = new Sort(in1, (short) len_in1, t1_str_sizes, am,
                    outFilter[0].operand1.symbol.offset, order[0], 30, n_buf_pgs); // sort with 1st predicate column

            Tuple tuple;
            while ((tuple = sort_object.get_next()) != null) {
                L1.add(new Tuple(tuple));
                L2.add(new Tuple(tuple));
            }

            //L1 is already sorted. we need to sort L2.

            int fldNo = outFilter[1].operand1.symbol.offset;
            if (order[1].tupleOrder == TupleOrder.Descending) {
                Collections.sort(L2, new Comparator<Tuple>() {
                    @Override
                    public int compare(Tuple t1, Tuple t2) {
                        int result = 0;
                        try {
                            result = TupleUtils.CompareTupleWithTuple(new AttrType(AttrType.attrInteger), t1, fldNo, t2, fldNo);
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                            Runtime.getRuntime().exit(1);
                        }
                        return result;
                    }
                });
            } else {
                Collections.sort(L2, new Comparator<Tuple>() {
                    @Override
                    public int compare(Tuple t1, Tuple t2) {
                        int result = 0;
                        try {
                            result = TupleUtils.CompareTupleWithTuple(new AttrType(AttrType.attrInteger), t2, fldNo, t1, fldNo);
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                            Runtime.getRuntime().exit(1);
                        }
                        return result;
                    }
                });
            }

            // Create P such that P[i] = j when L1[j] = L2[i]
            n = L1.size();

            P = new int[n];
            B = new boolean[n];

            //System.out.print("P = [");
            for (int k=0; k < n; k++) {
                Tuple L2_tuple = L2.get(k);
                for(int l=0; l < L1.size(); l++) {
                    if (TupleUtils.Equal(L2_tuple, L1.get(l), _in1, len_in1)) {
                        P[k] = l;
                    }
                }
            }
            j = P[i] + (eqOff? 1: 0);

        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            t_size = TupleUtils.setup_op_tuple(Jtuple, Jtypes, in1, len_in1, in1, len_in1,
                    t1_str_sizes, t1_str_sizes, proj_list, nOutFlds);
        } catch (TupleUtilsException e) {
            throw new NestedLoopException(e, "TupleUtilsException is caught by IEJoin.java");
        }

        try {
            hf = new Heapfile(relationName);
        } catch (Exception e) {
            throw new NestedLoopException(e, "IEJoin: Create new heapfile failed.");
        }
    }

    /**
     * @return The joined tuple is returned
     * @exception IOException               I/O errors
     * @exception JoinsException            some join exception
     * @exception IndexException            exception from super class
     * @exception InvalidTupleSizeException invalid tuple size
     * @exception InvalidTypeException      tuple type not valid
     * @exception PageNotReadException      exception from lower layer
     * @exception TupleUtilsException       exception from using tuple utilities
     * @exception PredEvalException         exception from PredEval class
     * @exception SortException             sort exception
     * @exception LowMemException           memory error
     * @exception UnknowAttrType            attribute type unknown
     * @exception UnknownKeyTypeException   key type unknown
     * @exception Exception                 other exceptions
     * 
     */
    public Tuple get_next()
            throws IOException, JoinsException, IndexException, InvalidTupleSizeException,
            InvalidTypeException, PageNotReadException, TupleUtilsException, PredEvalException,
            SortException, LowMemException, UnknowAttrType, UnknownKeyTypeException, Exception {
        
        while (i < n) {
            int pos = P[i];
            B[pos]= true;
            while (j < n) {
                if (B[j]) {
                    Tuple sol1 = L1.get(P[i]);
                    Tuple sol2 = L1.get(j);
                    Projection.Join(sol1, _in1, sol2, _in1, Jtuple, perm_mat, nOutFlds);
                    j++;
                    return Jtuple;
                }
                j++;
            }
            i++;
            if(i<n){
                j = P[i] + (eqOff? 1: 0);
            }
        }
        return null;
    }

    /**
     * implement the abstract method close() from super class Iterator to finish cleaning up
     * 
     * @exception IOException    I/O error from lower layers
     * @exception JoinsException join error from lower layers
     * @exception IndexException index access error
     */
    public void close() throws JoinsException, IOException, IndexException {
        if (!closeFlag) {
            try {
                outer.close();
            } catch (Exception e) {
                throw new JoinsException(e, "IEJoin_2b.java: error in closing iterator.");
            }
            closeFlag = true;
        }
    }
}
