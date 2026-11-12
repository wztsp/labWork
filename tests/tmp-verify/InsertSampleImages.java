import java.sql.*;

public class InsertSampleImages {
    public static void main(String[] args) throws Exception {
        long sampleId = Long.parseLong(args[0]);
        String url = "jdbc:postgresql://localhost:5432/lis_db";
        String user = "lis_user";
        String pass = "lis_dev_2026";
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            // clean existing for this sample
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM lis_sample_image WHERE sample_id = ?")) {
                ps.setLong(1, sampleId);
                ps.executeUpdate();
            }
            String[] names = {"\u56fe1-\u7ec6\u80de\u5f62\u6001", "\u56fe2-\u539f\u5c18\u6c89\u6e23", "\u56fe3-\u8840\u7ec6\u80de\u5206\u5e03"};
            String[] types = {"CELL_MORPHOLOGY", "URINE_SEDIMENT", "CELL_DISTRIBUTION"};
            String baseUrl = "https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?image_size=square_hd&prompt=";
            String[] prompts = {
                "\u8840\u7ec6\u80de\u5206\u5e03\u5f62\u6001\u5b66\u6b63\u5e38\u5fae\u89c2\u56fe\uff0cGiemsa\u67d3\u8272\uff0c\u591a\u89d2\u5ea6\u7ec6\u80de",
                "\u539f\u6c89\u6e23\u5fae\u89c2\u56fe\uff0c\u660e\u573a\u53ef\u53ef\uff0c\u539f\u6c10\u7ec6\u80de\u4e0e\u900f\u660e\u7ba1\u578b",
                "\u8840\u6db2\u6d82\u7247\u5fae\u89c2\u56fe\uff0c\u7ea2\u7ec6\u80de\u4e0e\u767d\u7ec6\u80de\u5404\u5f62\u6001"
            };
            try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO lis_sample_image (sample_id, image_url, image_name, image_type, sort_no) VALUES (?,?,?,?,?)")) {
                for (int i = 0; i < names.length; i++) {
                    ps.setLong(1, sampleId);
                    ps.setString(2, baseUrl + prompts[i]);
                    ps.setString(3, names[i]);
                    ps.setString(4, types[i]);
                    ps.setInt(5, i);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            System.out.println("Inserted 3 images for sample " + sampleId);
        }
    }
}