package db;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

//Here we define class imageNameFiletr, which implements
//FilenameFilter interface.
class ImageNameFiletr implements FilenameFilter {

	String[] acceptedSuffixes = null;

	// Here we define the constructor so that it receives
	// an array of user-defined suffixes.
	public ImageNameFiletr(String[] suffixes) {
		acceptedSuffixes = suffixes;
	}

	@Override
	public boolean accept(File dir, String name) {
		String suffix = "";
		int dotIndex = -1;
		// Here we check whether there's a dot in the name of
		// the file. If there's, then we read the suffix
		// or part of the file name after the dot.
		if ((dotIndex = name.indexOf('.')) != -1)
			suffix = name.substring(dotIndex + 1);
		// Here we check whether the file suffix equals to
		// one of the accepted ones. If there's one equality,
		// the method returns true.
		for (String s : acceptedSuffixes) {
			
			if (s.equalsIgnoreCase(suffix))
				return true;
		}
		return false;
	}
}

public class DBReadWriteImageServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	Connection conn = null;
	Statement stmt = null;
	ResultSet rs = null;
	PreparedStatement ps = null;
	String separator;
	String resourceDir;
	String imageDir;
	String table = "view";
	File imageDirFile;

	public void init() {
		separator = System.getProperty("file.separator");
		// Here we define the path for directory under which we have images
		resourceDir = getServletContext().getRealPath("resources") + separator;
		// Here we define the path for directory under which the servlet saves
		// images
		// read from the database
		imageDir = getServletContext().getRealPath("images") + separator;
		imageDirFile = new File(imageDir);
	}

	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String dbUserName = request.getParameter("db_username");
		String dbPassword = request.getParameter("db_password");
		String dbName = request.getParameter("db_name");
		String dbTableName = request.getParameter("db_table_name");
		if (imageDirFile.exists())
			imageDirFile.delete();
		imageDirFile.mkdir();
		/*
		 * Here we initialize tools for making the database connection and reading from
		 * the database
		 */
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		out.println("<html><head><title>Read Write Image Servlet</title></head><body>");
		try {
			// Here we load the database driver
			// Class.forName("oracle.jdbc.driver.OracleDriver");
			Class.forName("com.mysql.jdbc.Driver");
			// Here we set the JDBC URL for the Oracle database
			// String url="jdbc:oracle:thin:@db.cc.puv.fi:1521:ora817";
			String url = "jdbc:mysql://mysql.cc.puv.fi:3306/" + dbName;
			// Here we create a connection to the database
			// conn=DriverManager.getConnection(url, "scott", "tiger");
			conn = DriverManager.getConnection(url, dbUserName, dbPassword);
			// Here we create the statement object for executing SQL commands
			stmt = conn.createStatement();
			String[] acceptedImages = new String[] { "jpg", "gif", "png" };
			// Here we get a list of available images under resourceDir
			// directory. However, from the received list we only pick
			// files with specified suffixes.
			// String[] imageNames= new File(resourceDir).list(new
			// imageNameFiletr(acceptedImages));
			// Here we get the content of the resourceDir as a list of files.
			File[] imageFiles = new File(resourceDir).listFiles(new ImageNameFiletr(acceptedImages));
			// File image =null;
			FileInputStream fis = null;
			// Here we initialize the preparedStatement object
			ps = conn.prepareStatement("insert into " + dbTableName + "(name, image, image_size) " + "values(?,?,?)");
			for (int i = 0; i < imageFiles.length; i++) {
				// Here we set the name of the file as the value of the first
				// column.
				ps.setString(1, imageFiles[i].getName());
				// Here we define a file input stream for reading the content of
				// the file
				fis = new FileInputStream(imageFiles[i].getPath());
				// Here we set the input stream for the file as the value for
				// the
				// second column.
				ps.setBinaryStream(2, (InputStream) fis, (int) (imageFiles[i].length()));
				// Here we set the length of the file as the value of the third
				// column.
				ps.setLong(3, imageFiles[i].length());
				// Here we insert data to the table and read the returned value
				int counter = ps.executeUpdate();
				// Here we close the file input stream.
				fis.close();
				if (counter == 0)
					out.println("<p>" + imageFiles[i].getName() + " data was not uploaded sucessfully!");
			}

			// In the following we read data from the database.
			String imageQuery = "select * from " + dbTableName;
			ResultSet resultSet = stmt.executeQuery(imageQuery);
			File destinationFile = null;
			FileOutputStream fos = null;
			String name;
			int i = 0;
			while (resultSet.next()) {
				// Here we read the value of the first column of the table.
				name = resultSet.getString(1);
				// Here we create a File object, which refers to
				// the name read from the first column of the table
				destinationFile = new File(imageDir + name);
				// Here we prepare a FileOutputStream to write to the
				// destination file.
				fos = new FileOutputStream(destinationFile);
				// Here we initialize the inputStream by reading from
				// the second column of the table
				InputStream is = resultSet.getBinaryStream(2);
				// Here we reserve memory area to read the image
				// content.
				byte[] imageBuffer = new byte[is.available()];
				// Here we read the image data from the database to
				// the memory area.
				is.read(imageBuffer);
				// Here write the image data from memory to the
				// file.
				fos.write(imageBuffer);
				// Here we close the output and input streams.
				fos.close();
				fis.close();
				// Here we read the size of the image from the third
				// column of the table.
				long imageSize = resultSet.getLong(3);
				out.println("<p>" + name + " image size: " + imageSize);
				out.println("<img src='" + "images" + separator + name + "' alt='Error' width='150' height='150'/>");
				out.println("<hr>");
				i++;
			}
			out.println("<a href='index.html'>Back</a>");
			out.println("</body>");
			out.println("</html>");
		} catch (Exception e) {
			out.println(e.getMessage());
		} finally {
			try {
				if (stmt != null)
					stmt.close();
				if (conn != null)
					conn.close();
			} catch (SQLException e) {
				out.print("<p>" + e.getMessage());
			}
		}
	}

	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.sendRedirect("index.html");
	}
}