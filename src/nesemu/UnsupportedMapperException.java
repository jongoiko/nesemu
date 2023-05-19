package nesemu;

public class UnsupportedMapperException extends Exception {
    public final int mapperNumber;

    public UnsupportedMapperException(int mapperNumber) {
        this.mapperNumber = mapperNumber;
    }
}
