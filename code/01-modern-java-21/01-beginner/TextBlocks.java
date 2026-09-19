public class TextBlocks {

    public static void main(String[] args) {
        String json = """
                {
                  "name": "Uttam Kumar",
                  "role": "Backend Engineer"
                }
                """;

        String oldStyle = "{\n" +
                "  \"name\": \"Uttam Kumar\",\n" +
                "  \"role\": \"Backend Engineer\"\n" +
                "}\n";

        System.out.println(json);
        System.out.println("Text block equals concatenation: " + json.equals(oldStyle));
    }
}
