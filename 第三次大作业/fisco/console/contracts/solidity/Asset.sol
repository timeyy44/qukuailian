pragma solidity ^0.4.24;

import "./Table.sol";

contract Asset {
    // event
    event RegisterEvent(int256 ret, string account, int256 status);
    event OweEvent(int256 ret, string from_account, string to_account, uint256 amount);
    event ReturnEvent(int256 ret, string from_account, string to_account);
    event RequireEvent(int256 ret, string last_account, string from_account, string to_account, uint256 amount, int256  status);
    
    constructor() public {
        createTable();
    }
    
    function utilCompareInternal(string a, string b) internal returns (bool) {
        if (bytes(a).length != bytes(b).length) {
            return false;
        }
        for (uint i = 0; i < bytes(a).length; i ++) {
            if(bytes(a)[i] != bytes(b)[i]) {
                return false;
            }
        }
        return true;
    }
    
    string BANK = "bank";

    function createTable() private {
        TableFactory tf = TableFactory(0x1001); 
        tf.createTable("t_asset", "account", "status");
        tf.createTable("o_asset", "from_account", "to_account,amount,ddl");
    }

    function openTable(string tableName) private returns(Table) {
        TableFactory tf = TableFactory(0x1001);
        Table table = tf.openTable(tableName);
        return table;
    }

    function select(string account) public constant returns(int256, int256) {
        Table table = openTable("t_asset");
        int256 status = -1;
        Entries entries = table.select(account, table.newCondition());
        if (0 == uint256(entries.size())) {
            return (-1, status);
        } else {
            Entry entry = entries.get(0);
            return (0, int256(entry.getInt("status")));
        }
    }
    
    function select_o(string from_account, string to_account, int256 ddl) public constant returns(int256, uint256) {
        Table table = openTable("o_asset");
        uint256 amount_n = 0;
        
        Condition condition = table.newCondition();
        condition.EQ("from_account", from_account);
        condition.EQ("to_account", to_account);
        condition.EQ("ddl", ddl);
        
        Entries entries = table.select(from_account, condition);
        if (0 == uint256(entries.size())) {
            return (-1, amount_n);
        } else {
            Entry entry = entries.get(0);
            amount_n = uint256(entry.getInt("amount"));
            return (0, amount_n);
        }
        
    }
    
    function removeOver(string from_account, string to_account, int256 time) public constant returns(int256) {
        Table table = openTable("o_asset");
        
        Condition condition = table.newCondition();
        condition.EQ("from_account", from_account);
        condition.EQ("to_account", to_account);
        condition.LE("ddl", time);
        
        int count = table.remove(from_account, condition);
        if (0 == count) {
            return -1;
        } else {
            return 0;
        }
    }

    function register(string account, int256 status) public returns(int256){
        int256 ret_code = 0;
        int256 ret= 0;
        int256 status_o = status;
        
        (ret, status_o) = select(account);
        if(ret != 0) {
            if (utilCompareInternal(account, BANK)) {
                status_o = 0;
            }   
            
            Table table = openTable("t_asset");
            
            Entry entry = table.newEntry();
            entry.set("account", account);
            entry.set("status", status_o);
            // 插入
            int count = table.insert(account, entry);
            if (count == 1) {
                // 成功
                ret_code = 0;
            } else {
                // 失败? 无权限或者其他错误
                ret_code = -2;
            }
        } else {
            // 账户已存在
            ret_code = -1;
        }

        emit RegisterEvent(ret_code, account, status_o);

        return ret_code;
    }

    function owe(string from_account, string to_account, uint256 amount, int256 ddl) public returns(int256) {
        if (utilCompareInternal(to_account, BANK)) {
            return -1;
        }
        
        int ret = 0;
        int256 from_status = -1;
        int256 to_status = -1;
        uint256 amount_n = 0;
        
        (ret, from_status) = select(from_account);
        if (ret != 0) {
            emit OweEvent(-2, from_account, to_account, amount);
            return -2;
        }
        
        (ret, to_status) = select(to_account);
        if (ret != 0) {
            emit OweEvent(-3, from_account, to_account, amount);
            return -3;
        }
        
        (ret, amount_n) = select_o(to_account, from_account, ddl);
        if (ret != 0) {
            
        } else {
            if (amount > amount_n) {
                amount_n = amount - amount_n;
            } else {
                Table table1 = openTable("o_asset");
                table1.remove(from_account, table1.newCondition());
                amount_n = amount_n - amount;
                Entry entry1 = table1.newEntry();
                entry1.set("from_account", to_account);
                entry1.set("to_account", from_account);
                entry1.set("amount", int256(amount_n));
                entry1.set("ddl", ddl);
                table1.insert(to_account, entry);
                emit OweEvent(2, to_account, from_account, amount_n);
                return 2;
            }
        }
        
        Table table = openTable("o_asset");
        Entry entry = table.newEntry();
        entry.set("from_account", from_account);
        entry.set("to_account", to_account);
        entry.set("ddl", ddl);
        (ret, amount_n) = select_o(from_account, to_account, ddl);
        if (ret != 0) {
            amount_n = amount;
            entry.set("amount", int256(amount_n));
            table.insert(from_account, entry);
        } else {
            amount_n = amount + amount_n;
            entry.set("amount", int256(amount_n));
            table.update(from_account, entry, table.newCondition());
        }
        
        emit OweEvent(0, from_account, to_account, amount_n);
        return 0;
    }
    
    function retu(string from_account, string to_account, int256 time) public returns(int256) {
        if (utilCompareInternal(from_account, BANK) || utilCompareInternal(to_account, BANK)) {
            return -1;
        }
        
        int ret = 0;
        int ret_code = 0;
        int256 from_status = -1;
        int256 to_status = -1;
        
        (ret, from_status) = select(from_account);
        if (ret != 0) {
            ret_code = -2;
            emit ReturnEvent(ret_code, from_account, to_account);
            return ret_code;
        }
        
        (ret, to_status) = select(to_account);
        if (ret != 0) {
            ret_code = -3;
            emit ReturnEvent(ret_code, from_account, to_account);
            return ret_code;
        }
        
        ret_code = removeOver(from_account, to_account, time);
        emit ReturnEvent(ret_code, from_account, to_account);
        return ret_code;
    }
    
    function requ(string from_account, string to_account, string last_account, uint256 amount, int256 ddl) public returns(int256) {
        int ret = 0;
        int ret_code = 0;
        uint256 amount_n = 0;
        int256 status = -1;
        
        if (utilCompareInternal(to_account, BANK) || utilCompareInternal(last_account, BANK)) {
            return -1;
        }
        
        (ret, amount_n) = select_o(to_account, last_account, ddl);
        if (ret != 0) {
            ret_code = -2;
            emit RequireEvent(ret_code, from_account, to_account, last_account, amount, status);
            return ret_code;
        } else if (amount_n < amount) {
            ret_code = -3;
            emit RequireEvent(ret_code, from_account, to_account, last_account, amount, status);
            return ret_code;
        }
        
        (ret, status) = select(last_account);
        if (utilCompareInternal(from_account, BANK) && status == -1) {
            ret_code = -4;
            emit RequireEvent(ret_code, from_account, to_account, last_account, amount, status);
            return ret_code;
        }
        
        owe(from_account, last_account, amount, ddl);
        owe(last_account, to_account, amount, ddl);
        
        ret_code = 0;
        emit RequireEvent(ret_code, from_account, to_account, last_account, amount, status);
        return ret_code;
    }
}
