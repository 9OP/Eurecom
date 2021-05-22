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

public class IEJoin_2a extends Iterator {
    private AttrType _in1[];
    int inner_i = 0;
    int outer_i = 0;
    private int in1_len;
    private Iterator outer;
    private Sort inner;
    private Iterator _am;
    private TupleOrder order;
    private short t1_str_sizescopy[];
    private CondExpr OutputFilter[];
    private int n_buf_pgs; // # of buffer pages available.
    private boolean done, // Is the join complete
            get_from_outer; // if TRUE, a tuple is got from outer
    private Tuple outer_tuple, inner_tuple;
    private Tuple Jtuple; // Joined tuple
    private FldSpec perm_mat[];
    private int nOutFlds;
    private Heapfile hf;
    private ArrayList<Tuple> sortedTuples;

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
    public IEJoin_2a(AttrType in1[], int len_in1, short t1_str_sizes[], int amt_of_mem, Iterator am,
            String relationName, CondExpr outFilter[], FldSpec proj_list[],
            int n_out_flds) throws IOException, NestedLoopException {

        _in1 = new AttrType[in1.length];
        sortedTuples = new ArrayList<>();
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

        AttrType[] Jtypes = new AttrType[n_out_flds];
        short[] t_size;

        perm_mat = proj_list;
        nOutFlds = n_out_flds;

        // Sort differently depending on the operator
        if (outFilter[0].op.attrOperator == AttrOperator.aopGT
                || outFilter[0].op.attrOperator == AttrOperator.aopGE) {
            order = new TupleOrder(TupleOrder.Descending);
        } else {
            order = new TupleOrder(TupleOrder.Ascending);
        }

        try {
            // sort the tuples on the attribute we are considering for the where clause
            //TODO: is this really only for the operand 1?
            outer = am;
            while((outer_tuple = outer.get_next())!= null) {
                sortedTuples.add(new Tuple(outer_tuple));
            }
            int fldNo = outFilter[0].operand1.symbol.offset;
            if (order.tupleOrder == TupleOrder.Descending) {
                Collections.sort(sortedTuples, new Comparator<Tuple>() {
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
                Collections.sort(sortedTuples, new Comparator<Tuple>() {
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
        // This is a DUMBEST form of a join, not making use of any key information...

        while (outer_i < sortedTuples.size()) {

            outer_tuple = sortedTuples.get(outer_i);
            inner_tuple = sortedTuples.get(inner_i);
            
            //TODO: add support for <= and >=
            while(TupleUtils.CompareTupleWithTuple(new AttrType(AttrType.attrInteger), outer_tuple, OutputFilter[0].operand1.symbol.offset, inner_tuple, OutputFilter[0].operand1.symbol.offset) != 0) {
                Projection.Join(outer_tuple, _in1, inner_tuple, _in1, Jtuple, perm_mat, nOutFlds);
                inner_i++;
                inner_tuple = sortedTuples.get(inner_i);
                return Jtuple;
            }
            outer_i++;
            inner_i = 0;
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
                throw new JoinsException(e, "IEJoin.java: error in closing iterator.");
            }
            closeFlag = true;
        }
    }
}
