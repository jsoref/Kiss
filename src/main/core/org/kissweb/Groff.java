package org.kissweb;

import java.io.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Class to interface with the groff typesetting system.  This is an easy way to generate nicely formatted reports.
 * Of course, the underlying system must have groff/tbl/mm installed.
 * <br><br>
 * See: https://www.gnu.org/software/groff
 *
 * @author Blake McBride
 *
 */
public class Groff {

    private static Boolean isMac;
    private static Boolean isWindows;

    private final PrintWriter pw;
    private final String pdfname;
    private final String mmfname;
    private final boolean landscape;
    private final String title;
    private boolean atTop = true;
    private boolean autoPageHeader = true;
    private boolean deleteGroffFile = true;
    private String footerLeft = "";
    private String footerRight = "";
    private int numberOfColumns;
    private int currentColumn;
    private int currentRow;
    private String delim = "\f";
    private final StringBuilder row = new StringBuilder();
    private boolean inTitle = false;
    private boolean inTable = false;
    private String runDate;
    private final List<String> pageTitleLines = new ArrayList<>();
    private boolean grayEveryOtherLineFlg = true;

    /**
     * Initialize a new report.  The files it uses are put in temporary files
     * that are auto-cleaned and in a place that can be served to the front-end.
     * On the front-end side, see JavaScript Utils.showReport()
     *
     * @param fnamePrefix file name prefix
     * @param title     report title
     * @param landscape true if landscape format
     * @throws IOException
     */
    public Groff(String fnamePrefix, String title, boolean landscape) throws IOException {
        if (isMac == null) {
            String os = System.getProperty("os.name").toLowerCase();
            isMac = os.startsWith("mac ");
            isWindows = os.startsWith("windows");
        }
        File fyle = FileUtils.createReportFile(fnamePrefix, ".pdf");
        this.landscape = landscape;
        this.pdfname = fyle.getAbsolutePath();
        mmfname = pdfname.replaceAll("\\.pdf$", ".mm");
        pw = new PrintWriter(new BufferedWriter(new FileWriter(mmfname)));
        this.title = title;
    }

    /**
     * Do not auto-generate the page title, or if already printed, stop.
     */
    public void noAutoPageHeader() {
        if (autoPageHeader)
            autoPageHeader = false;
        else
            pw.println(".rm TP");
    }

    /**
     * Mark the start of a table.
     * <br><br>
     * <code>colFmt</code> is a string specifying the layout for each column as specified by tbl.
     *
     * @param colFmt
     */
    public void startTable(String colFmt) {
        grayEveryOtherLineFlg = true;  //  default
        colFmt = colFmt.trim();
        colFmt = colFmt.replaceAll(" {2}", " ");
        if (!colFmt.endsWith("."))
            colFmt += ".";
        numberOfColumns = 1 + colFmt.trim().replaceAll(" {2}", " ").replaceAll("[^ ]", "").length();
        out(".fi");
        out(".ad l");
        out(".TS H");
        out("center tab(" + delim + ");");
        out(colFmt);
        inTitle = true;
        inTable = true;
        currentColumn = currentRow = 0;
        row.setLength(0);
    }

    /**
     * Manually set the run date string.  An empty string (but not null) will cause no run date to be printed.
     *
     * @param rt
     */
    public void setRuntime(String rt) {
        runDate = rt;
    }

    private void flush() {
        if (currentColumn >= numberOfColumns) {
            if (grayEveryOtherLineFlg && !inTitle && currentRow++ % 2 == 1)
                pw.print("\\*Y");
            pw.println(row);
            currentColumn = 0;
            row.setLength(0);
        }
    }

    /**
     * Output a column (title or body of table)
     *
     * @param col
     */
    public void column(String col) {
        flush();
        if (currentColumn++ != 0)
            row.append(delim);
        if (col != null)
            row.append(col);
    }

    /**
     * Normally not needed.  Done automatically.  However, is needed to end a row prematurely.
     */
    public void endRow() {
        if (currentColumn == 0)
            return;
        while (currentColumn < numberOfColumns) {
            row.append(delim);
            currentColumn++;
        }
        flush();
    }

    /**
     * Output a column that may wrap vertically.
     *
     * @param col
     */
    public void columnWrap(String col) {
        /*  Automatic graying of every other row in a table is incompatible with wrapped columns.
            Therefore, it is turned off when wrapped columns are used.
         */
        grayEveryOtherLineFlg = false;
        flush();
        if (currentColumn++ != 0)
            row.append(delim);
        if (col != null)
            if (col.length() > 3) {
                row.append("T{\n");
                row.append(col);
                row.append("\nT}");
            } else
                row.append(col);
    }

    /**
     * Output a numeric double column
     * <br><br>
     * <code>msk</code> is a <code>String</code> consisting of the following characters:
     *
     * <ul>
     *     <li>B = blank if zero</li>
     *     <li>C = add commas</li>
     *     <li>L = left justify number</li>
     *     <li>P = put parentheses around negative numbers</li>
     *     <li>Z = zero fill</li>
     *     <li>D = floating dollar sign</li>
     *     <li>U = uppercase letters in conversion</li>
     *    <li>R = add a percent sign to the end of the number</li>
     * </ul>
     *
     * @param num the number to be output
     * @param msk   Format mask

     * @param dp number of decimal places (-1 means auto)
     */
    public void column(double num, String msk, int dp) {
        String col = NumberFormat.Format(num, msk, 0, dp);
        column(col);
    }

    /**
     * Output a numeric integer column
     * <br><br>
     * <code>msk</code> is a <code>String</code> consisting of the following characters:
     *
     * <ul>
     *     <li>B = blank if zero</li>
     *     <li>C = add commas</li>
     *     <li>L = left justify number</li>
     *     <li>P = put parentheses around negative numbers</li>
     *     <li>Z = zero fill</li>
     *     <li>D = floating dollar sign</li>
     *     <li>U = uppercase letters in conversion</li>
     *    <li>R = add a percent sign to the end of the number</li>
     * </ul>
     *
     * @param num the number to be output
     * @param msk   Format mask
     */
    public void column(int num, String msk) {
        String col = NumberFormat.Format((double) num, msk, 0, 0);
        column(col);
    }

    /**
     * Output an integer date column.  Formats date as MM/DD/YYYY
     *
     * @param date integer date formatted as YYYYMMDD
     */
    public void dateColumn(int date) {
        String col = DateUtils.format4(date);
        column(col);
    }

    /**
     * Output an integer date column.  Formats date as MM/DD/YY
     *
     * @param date integer date formatted as YYYYMMDD
     */
    public void dateColumn2(int date) {
        String col = DateUtils.format2(date);
        column(col);
    }

    /**
     * Output a Date date column.  Formats date as MM/DD/YYYY
     *
     * @param date
     */
    public void dateColumn(Date date) {
        String col = DateUtils.format4(DateUtils.toInt(date));
        column(col);
    }

    /**
     * Mark the end of the title
     */
    public void endTitle() {
        pw.println(row);
        row.setLength(0);
        currentColumn = 0;
        for (int i=0 ; i < numberOfColumns ; i++) {
            if (i != 0)
                pw.print(delim);
            pw.print("\\_");
        }
        pw.println();
        pw.println(".TH");
        inTitle = false;
    }

    /**
     * To be called at the end of the table
     */
    public void endTable() {
        if (inTable) {
            if (row.length() > 0) {
                if (row.toString().startsWith("T{\n")) {
                    pw.print("T{\n");
                    if (grayEveryOtherLineFlg && !inTitle && currentRow++ % 2 == 1)
                        pw.print("\\*Y");
                    pw.println(row.delete(0, 3));
                } else {
                    if (grayEveryOtherLineFlg && !inTitle && currentRow++ % 2 == 1)
                        pw.print("\\*Y");
                    pw.println(row);
                }
                row.setLength(0);
            }
            pw.println(".TE");
            pw.println(".nf");
            inTable = false;
            currentColumn = currentRow = 0;
        }
        inTitle = false;
    }

    /**
     * Output a line in bold text.
     *
     * @param txt
     */
    public void outBold(String txt) {
        out(".B \"" + txt + "\"");
    }

    /**
     * Add an additional line to the page title.
     *
     * @param line
     */
    public void addPageTitleLine(String line) {
        pageTitleLines.add(line);
    }

    private void setDefaults() {
        pw.println(".PH \"''''\"");
        pw.println(".PF \"'" + footerLeft + "'Page \\\\\\\\nP'" + footerRight + "'\"");
        pw.println(".S 11");
        pw.println("'nf");

        // Code to help make every other line gray
        pw.println(".\\\" ----------------------------------------------------------------");
        pw.println(".defcolor lightgray gray 0.95");
        pw.println(".fcolor lightgray");
        pw.println(".nr TW 0");
        pw.println(".nr LW 0.2p");
        pw.println(".ds Y \\Z'\\h'-\\\\n[LW]u'\\v'0.22v'\\D'P 0 -.9v 2u*\\\\n[LW]u+\\\\n[TW]u 0 0 .9v''");
        pw.println(".\\\" ----------------------------------------------------------------");
    }

    private void writePageHeader(String title) {
        pw.println(".de TP");
        pw.println("'SP .5i");
        if (runDate == null)
            pw.println("'tl '''" + DateTime.currentDateTimeFormattedTZ() + "'");
        else if (!runDate.isEmpty())
            pw.println("'tl '''" + runDate + "'");
        pw.println("'ce " + (1 + pageTitleLines.size()));
        pw.println(title);
        for (String line : pageTitleLines)
            pw.println(line);
        pw.println("'SP");
        pw.println("..");
    }

    /**
     * Prevent the deletion of the intermediate Groff file for debugging purposes.
     */
    public void dontDeleteGroffFile() {
        deleteGroffFile = false;
    }

    /**
     * Write a line to the groff input file.
     *
     * @param str
     */
    public void out(String str) {
        if (atTop) {
            setDefaults();
            atTop = false;
        }
        if (autoPageHeader) {
            writePageHeader(title);
            autoPageHeader = false;
        }
        if (str != null)
            pw.println(str);
    }

    /**
     * Process the groff/tbl/mm input, produce the PDF output file, and return the path to the PDF file.
     * The file name returned is suitable for a web server and not the absolute file path.
     *
     * @param sideMargin size of the margin on the left side of the page in inches
     * @return
     * @throws IOException
     * @throws InterruptedException
     * @see #getRealFileName()
     */
    public String process(float sideMargin) throws IOException, InterruptedException {
        if (inTable)
            endTable();
        ProcessBuilder builder;
        String psfname = null;
        out(null);
        pw.flush();
        pw.close();
        if (isMac) {
            if (landscape)
                builder = new ProcessBuilder("/bin/sh", "-c", "groff -mm -t -P-pletter -rL=8.5i -P-l -rO=" + sideMargin + "i -rW=" + (11 - 2 * sideMargin) + "i " + mmfname + " |pstopdf -i -o " + pdfname);
            else
                builder = new ProcessBuilder("/bin/sh", "-c", "groff -mm -t -P-pletter -rL=11i -rO=" + sideMargin + "i -rW=" + (8.5 - 2 * sideMargin) + "i " + mmfname + " |pstopdf -i -o " + pdfname);
            builder.redirectError(new File("/dev/null"));
        } else if (isWindows) {
            psfname = pdfname.replaceAll("\\.pdf$", ".ps");
            if (landscape)
                builder = new ProcessBuilder("cmd", "/c", "groff", "-mm", "-t", "-P-pletter", "-rL=8.5i", "-P-l", "-rO=" + sideMargin + "i", "-rW=" + (11 - 2 * sideMargin) + "i", mmfname);
            else
                builder = new ProcessBuilder("cmd", "/c", "groff", "-mm", "-t", "-P-pletter", "-rL=11i", "-rO=" + sideMargin + "i", "-rW=" + (8.5 - 2 * sideMargin) + "i", mmfname);
            builder.redirectError(new File("NUL:"));
        } else {
            if (landscape)
                builder = new ProcessBuilder("groff", "-mm", "-t", "-Tpdf", "-P-pletter", "-rL=8.5i", "-P-l", "-rO="+sideMargin+"i", "-rW=" + (11-2*sideMargin) + "i", mmfname);
            else
                builder = new ProcessBuilder("groff", "-mm", "-t", "-Tpdf", "-P-pletter", "-rL=11i", "-rO="+sideMargin+"i", "-rW=" + (8.5-2*sideMargin) + "i", mmfname);
            builder.redirectError(new File("/dev/null"));
        }
        builder.redirectOutput(new File(pdfname));
        Process p = builder.start();
        boolean r = p.waitFor(1L, TimeUnit.MINUTES);
        if (deleteGroffFile)
            (new File(mmfname)).delete();
        if (!r)
            throw new InterruptedException();
        if (isWindows) {
            builder = new ProcessBuilder("cmd", "/c", "ps2pdf.bat", psfname, pdfname);
            p = builder.start();
            r = p.waitFor(1L, TimeUnit.MINUTES);
            (new File(psfname)).delete();
            if (!r)
                throw new InterruptedException();
        }
        return FileUtils.getHTTPPath(pdfname);
    }

    /**
     * Process the groff/tbl/mm input, produce the PDF output file, and return the path to the PDF file.
     * Defaults to a 1-inch side margin.
     *
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    public String process() throws IOException, InterruptedException {
        return process(1f);
    }

    /**
     * Ability to customize what shows on the bottom of each page.
     *
     * @param left   appears on the bottom left
     * @param right  appears on the bottom right
     */
    public void setFooter(String left, String right) {
        footerLeft = left == null ? "" : left;
        footerRight = right == null ? "" : right;
    }

    /**
     * The file name returned elsewhere is one convenient for web applications.
     * This method returns the real file name - not relative to a web server.
     */
    public String getRealFileName() {
        return pdfname;
    }

    /**
     * For debugging.  Returns the intermediate groff file.  This only makes sense if <code>dontDeleteGroffFile()</code>
     * is called.
     *
     * @return The intermediate troff/groff source file.
     */
    public String getGroffFileName() {
        return mmfname;
    }
}
