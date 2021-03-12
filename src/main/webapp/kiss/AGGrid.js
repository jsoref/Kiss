'use strict';


/**
 * This class is a wrapper around the public ag-grid utility.  It is intended that this class be used exclusively
 * rather than any of the raw ag-grid API.  It provides a higher-level and more convenient API.
 * <br><br>
 * Please refer to agGrid documentation on <a href="https://www.ag-grid.com/documentation-main/documentation.php">https://www.ag-grid.com/documentation-main/documentation.php</a> for more information.
 *
 */
class AGGrid {
    /**
     * Create a new AGGrid instance.
     * <br><br>
     * The HTML portion of this should look like:
     * <br><br>
     * <code>&lt;div id="grid" style="margin-top: 10px; width: 100%; height: calc(100vh - 240px);"&gt;&lt;/div&gt;</code>
     * <br><br>
     * CSS <code>calc</code> can be used to set the width or height so that the grid dynamically resizes when the browser window
     * gets resized.
     *
     * @param id {string} the ID of the div that represents the grid
     * @param columns an ag-grid columnDefs data structure
     * @param keyColumn {string} the ID of the key column (optional)
     */
    constructor(id, columns, keyColumn=undefined) {
        this.id = id;
        this.columns = columns;
        this.rowSelection = AGGrid.SINGLE_SELECTION;
        this.data = [];
        this.keyColumn = keyColumn;
        this.components = {};
        this.gridInstantiated = false;
        this.highlightedRows = [];
        this.dragFunction = null;
        this.suppressRowClickSelection = false;
        this.suppressHorizontalScroll = true;
    }

    /**
     * Initialize and show the grid.
     * <br><br>
     * Important!  Once this is called, you must call the <code>destroy()</code> method when the grid is no longer needed.
     *
     * @returns {AGGrid}
     */
    show() {
        const self = this;
        this.gridOptions = {
            columnDefs: this.columns,
            rowData: this.data,
            rowSelection: this.rowSelection,
            rowDeselection: this.rowSelection === AGGrid.MULTI_SELECTION,
            suppressHorizontalScroll: this.suppressHorizontalScroll,
            suppressCellSelection: true,
            components: this.components,
            suppressRowClickSelection: this.suppressRowClickSelection,
            suppressRowHoverHighlight: this.suppressRowClickSelection,
            onRowDragEnd: function (e) {
                if (self.dragFunction)
                    self.dragFunction(e.node.data, e.overNode.data);
            },
            defaultColDef: {
                resizable: true
            },
            onGridReady: function (params) {
                if (this.suppressHorizontalScroll) {
                    params.api.sizeColumnsToFit();

                    window.addEventListener('resize', function() {
                        setTimeout(function() {
                            params.api.sizeColumnsToFit();
                        });
                    });
                }
            }/* ,
            getRowNodeId: function (data) {
                return data[self.keyColumn];
            }
            */
            // Disable all warnings
            // suppressPropertyNamesCheck: true
        };

        if (self.keyColumn)
            this.gridOptions.getRowNodeId = data => data[self.keyColumn];

        let eGridDiv = document.querySelector('#' + this.id);
        if (!eGridDiv)
            console.log("grid id " + this.id + " does not exist");
        else {
            eGridDiv.classList.add('ag-theme-balham');
            new agGrid.Grid(eGridDiv, this.gridOptions);
            this.gridInstantiated = true;
        }
        if (!AGGrid.gridContext.length)
            AGGrid.newGridContext();
        AGGrid.addGrid(this);
        return this;
    }

    resizeColumns() {
        this.gridOptions.api.sizeColumnsToFit();
        return this;
    }

    /**
     * Free all of the internal data structures associated with the grid.
     * <br><br>
     * This method must be called once a grid is no longer needed.
     */
    destroy() {
        if (this.gridInstantiated) {
            this.gridOptions.api.destroy();
            this.gridOptions = null;
            this.id = null;
            this.columns = null;
            this.rowSelection = null;
            this.data = null;
            this.keyColumn = null;
            this.components = null;
            this.gridInstantiated = false;
        }
    }

    /**
     * If a row is dragged, this function will be called when the drag operation is complete.  <code>fun</code> is
     * passed two arguments.  The first is the row being dragged.  The second is the row over which it was released.
     *
     * @param fun {function}
     * @returns {AGGrid}
     */
    setDragFunction(fun) {
        this.dragFunction = fun;
        return this;
    }

    /**
     * Erase all of the rows in the grid.
     *
     * @returns {AGGrid}
     */
    clear() {
        this.gridOptions.api.setRowData([]);
        return this;
    }

    setRowData(data) {
        if (!this.gridOptions)
            this.data = data;
        else
            this.gridOptions.api.setRowData(data);
        return this;
    }

    /**
     * Delete the row who's key column is equal to <code>id</code>.
     *
     * @param id
     * @returns {AGGrid}
     */
    deleteRow(id) {
        const node = this.gridOptions.api.getRowNode(id);
        if (node  &&  node.data)
            this.gridOptions.api.updateRowData({remove: [node.data]});
        return this;
    }

    /**
     * Disallow any row selection.  This must be called before <code>show()</code>
     *
     * @returns {AGGrid}
     */
    noRowSelection() {
        this.suppressRowClickSelection = true;
        return this;
    }

    /**
     * Highlight a row or an array of particular rows.
     * <br><br>
     * Note that highlighting a row and selecting a row are two different things.
     * <br><br>
     * Pass row index or an array or row indexes to highlight.
     * Pass null to un-highlight all rows.
     *
     * @param idx  null, number, or array of numbers
     */
    highlightRows(idx) {
        this.highlightedRows = idx = Utils.assureArray(idx);
        this.gridOptions.getRowStyle = (params) => {
            for (let i=0 ; i < idx.length ; i++)
                if (params.node.rowIndex === idx[i])
                    return { background: 'lightPink' };
            return null;
        };
        this.gridOptions.api.deselectAll();
        this.gridOptions.api.redrawRows();
        return this;
    }

    /**
     * Returns an array of the highlighted row indexes.
     *
     * @returns {array}
     */
    getHighlightedRowIndexes() {
        return this.highlightedRows;
    }

    /**
     * Select the row specified in which the key column of that row is equal to <code>id</code>.
     *
     * @param id
     * @returns {AGGrid}
     */
    selectId(id) {
        const node = this.gridOptions.api.getRowNode(id);
        if (node  &&  node.data)
            node.setSelected(true, true);
        return this;
    }

    /**
     * De-select all rows.
     *
     * @returns {AGGrid}
     */
    deselectAll() {
        this.gridOptions.api.deselectAll();
        return this;
    }

    /**
     * Add an array of records to the grid.
     * <br><br>
     * Note that this method is far faster than a series of <code>addRecord()</code> calls.
     *
     * @param data {array} each element of the array is an object representing a row
     * @returns {AGGrid}
     */
    addRecords(data) {
        data = Utils.assureArray(data);
        if (!this.gridOptions)
            this.data = this.data.concat(data);
        else {
            this.gridOptions.api.updateRowData({add: data});
            if (this.suppressHorizontalScroll)
                this.resizeColumns();  // when vert scrollbar gets auto-added must resize columns
        }
        return this;
    }

    /**
     * Add a single record to the grid.
     * <br><br>
     * Note that if several records are going to be added at a time, it is far faster to use <code>addRecords()</code>.
     *
     * @param data {object} each element represents a column on the grid
     * @returns {AGGrid}
     */
    addRecord(data) {
        if (!this.gridOptions)
            this.data.push(data);
        else
            this.gridOptions.api.updateRowData({add: [data]});
        return this;
    }

    /**
     * Clear the selection from the currently selected rows.
     *
     * @returns {AGGrid}
     */
    clearSelection() {
        if (this.gridOptions)
            this.gridOptions.api.deselectAll();
        return this;
    }

    /**
     * Update the selected row with the new data provided in <code>row</code>.
     *
     * @param row
     * @returns {AGGrid}
     */
    updateSelectedRecord(row) {
        this.gridOptions.api.updateRowData({update: [row]});
        return this;
    }

    /**
     * Delete the selected rows.
     *
     * @returns {AGGrid}
     */
    deleteSelectedRows() {
        const selectedData = this.gridOptions.api.getSelectedRows();
        this.gridOptions.api.updateRowData({remove: selectedData});
        return this;
    }

    /**
     * Return an array containing all of the selected rows.
     *
     * @returns {*}
     */
    getSelectedRows() {
        return this.gridOptions.api.getSelectedRows();
    }

    /**
     * Returns all of the rows.
     *
     * @returns {array}
     */
    getAllRows() {
        const rows = [];
        this.gridOptions.api.forEachNode(node => rows.push(node.data));
        return rows;
    }

    /**
     * Returns the number of rows in the grid.
     *
     * @returns {number}
     */
    getNumberOfRows() {
        return this.gridOptions.api.rowModel.getRowCount();
    }

    /**
     * Get row at index <code>n</code> (indexes are zero origin)
     *
     * @param n
     * @returns {*}
     */
    getRowAtIndex(n) {
        const rows = this.getAllRows();
        return rows[n];
    }

    /**
     * Return the index of the selected row.  <code>null</code> is returned if no row is selected.
     * If multiple rows are selected, the index of the forst selected row is returned.
     *
     * @returns {null|number}
     */
    getSelectedRowIndex() {
        const rows = this.getAllRows();
        const selectedRow = this.getSelectedRow();
        const key = selectedRow[this.keyColumn];
        for (let i=0 ; i < rows.length ; i++)
            if (key === rows[i][this.keyColumn])
                return i;
        return null;
    }

    /**
     * Return an array of the indexes of all of the selected rows.
     *
     * @returns {array}
     */
    getSelectedRowIndexes() {
        const rows = this.getAllRows();
        const selectedRows = this.getSelectedRows();
        const lst = [];
        for (let i=0 ; i < rows.length ; i++)
            for (let j=0 ; j < selectedRows.length ; j++)
                if (selectedRows[j][this.keyColumn] === rows[i][this.keyColumn]) {
                    lst.push(i);
                    break;
                }
        return lst;
    }

    /**
     * Delete row at index <code>n</code>.
     *
     * @param n
     * @returns {AGGrid}
     */
    deleteRowIndex(n) {
        const row = this.getRowAtIndex(n);
        this.gridOptions.api.updateRowData({remove: [row]});
        return this;
    }

    /**
     * Return the selected row or <code>null</code> if none is selected.
     *
     * @returns {*}
     */
    getSelectedRow() {
        const sel = this.gridOptions.api.getSelectedRows();
        return sel.length === 1 ? sel[0] : null;
    }

    /**
     * Returns the number of selected rows
     *
     * @returns {*}
     */
    numberOfSelectedRows() {
        return this.gridOptions.api.getSelectedRows().length;
    }

    getDataItems() {
        const dataItems = [];
        this.gridOptions.api.rowModel.forEachNode(node => {
            dataItems.push(node.data);
        });
        return dataItems;
    }

    /**
     * Execute <code>fn</code> anytime a row is selected or the selection changes.
     *
     * @param fn {function}
     */
    setOnSelection(fn) {
        const self = this;
        this.gridOptions.onRowSelected = function () {
            if (self.gridOptions.api.getSelectedRows().length  &&  fn)
                fn();
        };
        return this;
    }

    /**
     * Execute function <code>fn</code> whenever the user double-clicks on a row.
     *
     * @param fn {function}
     * @returns {AGGrid}
     */
    setOnRowDoubleClicked(fn) {
        this.gridOptions.onRowDoubleClicked = fn;
        return this;
    }

    /**
     * Return <code>true</code> if the grid is empty.
     *
     * @returns {boolean}
     */
    isEmpty() {
        return this.gridOptions.api.rowModel.getRowCount() === 0;
    }

    sizeColumnsToFit() {
        if (this.gridOptions === undefined)
            console.error("Grid options not found. Make sure the grid is built using the 'build' method.");
        else
            this.gridOptions.api.sizeColumnsToFit();
        return this;
    }

    /**
     * Add a class that defines special cell formatting.
     *
     * @param tag
     * @param cls
     * @returns {AGGrid}
     */
    addComponent(tag, cls) {
        this.components[tag] = cls;
        return this;
    }

    /**
     * By default, grids are single-row-select enabled.  This method enables multi-row-select.
     * It must be called prior to <code>show()</code>.
     *
     * @returns {AGGrid}
     */
    multiSelect() {
        this.rowSelection = AGGrid.MULTI_SELECTION;
        return this;
    }

    /**
     * Create a new grid context.
     */
    static newGridContext() {
        AGGrid.gridContext.push([]);
    }

    /**
     * Add a grid to the current context.
     *
     * @param grid
     */
    static addGrid(grid) {
        const cc = AGGrid.gridContext[AGGrid.gridContext.length - 1];
        cc.push(grid);
    }

    /**
     * Destroy all grids in last context and remove the context
     */
    static popGridContext() {
        const c = AGGrid.gridContext.pop();
        if (c)
            for (let i = 0; i < c.length; i++)
                c[i].destroy();
    }

    /**
     * destroys all popup and screen grids that have been created
     */
    static popAllGridContexts() {
        while (AGGrid.gridContext.length)
            AGGrid.popGridContext();
    }

}

// class variables
AGGrid.SINGLE_SELECTION = 'single';
AGGrid.MULTI_SELECTION = 'multiple';

AGGrid.gridContext = [];        //  An array of arrays.  The outer array represents a stack of contexts.
                                //  The inner array is an array of grids that'll need to be disposed.
                                //  Basically, each context (except the first) represents a popup.
                                //  The first represents the current screen.
                                //  Each inner array contains an array of grids in that context.