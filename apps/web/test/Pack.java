// The packing of android/bridge/.../StoreLink.kt (java.util.zip.Deflater with nowrap, then base64url without padding),
// run here to check that what the Android app puts in the address is what the page reads.
// Usage: java Pack.java <file>   (prints the base64url payload)
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.zip.Deflater;

public class Pack {
    public static void main(String[] args) throws Exception {
        byte[] code = Files.readAllBytes(Path.of(args[0]));
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        ByteArrayOutputStream packed = new ByteArrayOutputStream();
        deflater.setInput(code);
        deflater.finish();
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) packed.write(buffer, 0, deflater.deflate(buffer));
        deflater.end();
        System.out.print(Base64.getUrlEncoder().withoutPadding().encodeToString(packed.toByteArray()));
    }
}
