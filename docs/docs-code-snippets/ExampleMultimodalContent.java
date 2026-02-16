import ai.koog.prompt.dsl.ContentPartsBuilder;
import ai.koog.prompt.dsl.Prompt;
import ai.koog.prompt.message.AttachmentContent;
import ai.koog.prompt.message.ContentPart;
import java.util.List;

public class ExampleMultimodalContent {
    public static void main(String[] args) {
        //* Auto-configured attachments */
        ContentPartsBuilder partsBuilder = new ContentPartsBuilder();
        partsBuilder.text("Describe these images:");

        // [TODO] What is the Java equivalent of image(Path("/path/to/image.jpg")) ?
        // The suggested partsBuilder.image(new Path("/path/to/image.jpg")) doesn't work

        partsBuilder.image("https://example.com/test.png");
        partsBuilder.text("Focus on the main subjects.");

        Prompt prompt0 = Prompt.builder("image_analysis")
            .user(partsBuilder.build())
            .build();


        /* Custom-configured attachments */
        Prompt prompt1 = Prompt.builder("custom_image")
            .user(List.of(
                new ContentPart.Text("Describe this image"),
                new ContentPart.Image(
                    new AttachmentContent.URL("https://example.com/capture.png"),
                    "png",
                    "image/png",
                    "capture.png"
                )
            ))
            .build();

        /* Mixed attachments */
        Prompt prompt2 = Prompt.builder("mixed_content_example")
            .system("You are a helpful assistant.")
            .user(List.of(
                new ContentPart.Text("Please analyze this image and the attached document."),
                new ContentPart.Image(
                    new AttachmentContent.URL("https://example.com/image.png"),
                    "png",
                    "image/png",
                    "image.png"
                ),
                new ContentPart.File(
                    new AttachmentContent.URL("https://example.com/document.pdf"),
                    "pdf",
                    "application/pdf",
                    "document.pdf"
                ),
                new ContentPart.Text("Summarize the differences.")
            ))
            .build();
    }
}
