/*******************************************************************************
 * oltpbenchmark.com
 *  
 *  Project Info:  http://oltpbenchmark.com
 *  Project Members:    Carlo Curino <carlo.curino@gmail.com>
 *              Evan Jones <ej@evanjones.ca>
 *              DIFALLAH Djellel Eddine <djelleleddine.difallah@unifr.ch>
 *              Andy Pavlo <pavlo@cs.brown.edu>
 *              CUDRE-MAUROUX Philippe <philippe.cudre-mauroux@unifr.ch>  
 *                  Yang Zhang <yaaang@gmail.com> 
 * 
 *  This library is free software; you can redistribute it and/or modify it under the terms
 *  of the GNU General Public License as published by the Free Software Foundation;
 *  either version 3.0 of the License, or (at your option) any later version.
 * 
 *  This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 ******************************************************************************/
package com.oltpbenchmark.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.sql.Types;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Iterator;

import com.oltpbenchmark.catalog.Column;
import com.oltpbenchmark.catalog.Table;

import au.com.bytecode.opencsv.CSVReader;

/**
 * @author pavlo
 */
public class TableDataIterable implements Iterable<Object[]> {
    private final Table catalog_tbl;
    private final File table_file;
    private final CSVReader reader;
    private final boolean auto_generate_first_column;
    
    private final int types[];
    private final boolean fkeys[];
    private final boolean nullable[];
    
    private final DateFormat timestamp_formats[] = new DateFormat[] {
        new SimpleDateFormat("yyyy-MM-dd"),
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"),
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS"),
    };
    private int line_ctr = 0;
    
    /**
     * Constructor
     * @param table_file
     * @param has_header whether we expect the data file to include a header in the first row
     * @param auto_generate_first_column TODO
     * @throws Exception
     */
    public TableDataIterable(Table catalog_tbl, File table_file, boolean has_header, boolean auto_generate_first_column) throws Exception {
        this.catalog_tbl = catalog_tbl;
        this.table_file = table_file;
        this.auto_generate_first_column = auto_generate_first_column;
        
        this.types = new int[this.catalog_tbl.getNumColumns()];
        this.fkeys = new boolean[this.catalog_tbl.getNumColumns()];
        this.nullable = new boolean[this.catalog_tbl.getNumColumns()];
        for (int i = 0; i < this.types.length; i++) {
            Column catalog_col = this.catalog_tbl.getColumn(i);
            this.types[i] = catalog_col.getType();
            this.fkeys[i] = false; // TODO
            this.nullable[i] = catalog_col.isNullable();
        } // FOR
        
        BufferedReader in = new BufferedReader(new FileReader(this.table_file));
        this.reader = new CSVReader(in);
        
        // Throw away the first row if there is a header
        if (has_header) {
            this.reader.readNext();
            this.line_ctr++;
        }
    }
    
    
    public Iterator<Object[]> iterator() {
        return (new TableIterator());
    }
    
    public class TableIterator implements Iterator<Object[]> {
        String[] next = null;

        private void getNext() {
            if (next == null) {
                try {
                    next = reader.readNext();
                } catch (Exception ex) {
                    throw new RuntimeException("Unable to retrieve tuples from '" + table_file + "'", ex);
                }
            }
        }
        
        @Override
        public boolean hasNext() {
            this.getNext();
            return (next != null);
        }
        
        @Override
        public Object[] next() {
            this.getNext();
            if (next == null) return (next);
            String row[] = null;
            synchronized (this) {
                row = this.next;
                this.next = null;
            } // SYNCH
            
            Object tuple[] = new Object[types.length];
            int row_idx = 0;
            for (int col_idx = 0; col_idx < types.length; col_idx++) {
                // Auto-generate first column
                if (col_idx == 0 && auto_generate_first_column) {
                    tuple[col_idx] = new Long(line_ctr) ;
                }
                // Null Values
                else if (row_idx >= row.length) {
                    tuple[col_idx] = null;
                }
                // Foreign Keys
                else if (fkeys[col_idx]) {
                    tuple[col_idx] = row[row_idx++];
                }
                // Timestamps
                else if (types[col_idx] == Types.TIMESTAMP) {
                    for (DateFormat f : timestamp_formats) {
                        try {
                            tuple[col_idx] = f.parse(row[row_idx]);
                        } catch (ParseException ex) {
                            // Ignore...
                        }
                        if (tuple[col_idx] != null) break;
                    } // FOR
                    if (tuple[col_idx] == null) {
                        throw new RuntimeException(String.format("Line %d: Invalid timestamp format '%s' for column #%d",
                                                                 TableDataIterable.this.line_ctr, row[row_idx], col_idx));
                    }
                    row_idx++;
                }
                // Store string (truncate if necessary)
                else if (types[col_idx] == Types.VARCHAR) {
                    tuple[col_idx] = row[row_idx++];
                }    
                // Default: Cast the string into the proper type
                else {
                    if (row[row_idx].isEmpty() && nullable[col_idx]) {
                        tuple[col_idx] = null;
                    } else {
                        // TODO
                    }
                    row_idx++;
                }
            } // FOR
            TableDataIterable.this.line_ctr++;
            return (tuple);
        }
        
        @Override
        public void remove() {
            throw new RuntimeException("Unimplemented operation");
        }
    }
}