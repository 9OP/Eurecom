#!/bin/python3
import sqlite3
import os
from sqlite3 import Error

DB = 'query_checker_SQLite3.db'
TABLES = {
'R': '''
CREATE TABLE R (
    int1 INTEGER,
    int2 INTEGER,
    int3 INTEGER,
    int4 INTEGER
);
''',
'S': '''
CREATE TABLE S (
    int1 INTEGER,
    int2 INTEGER,
    int3 INTEGER,
    int4 INTEGER
);
'''
}


class Database:
    def __init__(self, db_file=DB):
        self.db_file = db_file
        self._exists = os.path.isfile(db_file)
        self._connection = self._create_connection()
        self._cursor = self._connection.cursor()

    def __del__(self):
        self._connection.commit()
        self._cursor.close()
        self._connection.close()

    def _create_connection(self):
        ''' create a database connection to the SQLite database
            specified by db_file
        :param db_file: database file
        :return: Connection object or None
        '''
        conn = None
        try:
            conn = sqlite3.connect(self.db_file)
        except Error as e:
            print(e)
        return conn

    def _create_table(self, table):
        ''' create TABLES[table] in database
        :param table: key in TABLES global variable - table name
        '''
        try:
            self._cursor.execute(TABLES[table])
            self._connection.commit()
        except Error as e:
            print(e)

    def _insert_record(self, record, table):
        ''' insert record in table
        :param record: record to insert
        :param table: TABLE key ('R' or 'S')
        '''
        self._cursor.execute('''
            SELECT count(name) FROM sqlite_master WHERE type='table' AND name=?
        ''', (table, ))
        if self._cursor.fetchone()[0]!=1: # create table if doesn't exist
            self._create_table(table)

        self._cursor.execute(' \
            INSERT INTO {} VALUES ({},{},{},{}) \
        '.format(table, record[0], record[1], record[2], record[3]))

    def _insert_data(self, path, table):
        ''' insert file (S.txt or R.txt) in database
        :param path: path to S.txt or R.txt
        '''
        cpt = 0
        with open(path, 'r') as outfile:
            line = outfile.readline() # first line contains only attr types
            line = outfile.readline()
            while line and cpt <3:
                cpt += 1
                record = [int(e) for e in line.split(',')]
                self._insert_record(record, table)
                line = outfile.readline().rstrip()
        self._connection.commit()

    def _execute_query(self, path):
        self._cursor.execute('''
            SELECT R.int1, S.int1 FROM R, S WHERE R.int3 < S.int3
        ''')
        res = self._cursor.fetchall()
        with open('Queries_result/'+path, 'w') as infile:
            for line in res:
                infile.write(str(list(line))+'\n')

    def check_queries(self):
        if not self._exists:
            self._insert_data('R.txt', 'R')
            self._insert_data('S.txt', 'S')
        queries = [
            'query_1a.txt',
            # 'query_1b.txt',
            # 'query_2a.txt',
            # 'query_2b.txt',
            # 'query_2c.txt',
            # 'query_2c_1.txt',
            # 'query_2c_2.txt'
        ]
        for query in queries:
            self._execute_query(query)


if __name__ == '__main__':
    database = Database();
    database.check_queries()
