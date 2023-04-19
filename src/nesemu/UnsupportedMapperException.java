package nesemu;

public class UnsupportedMapperException extends Exception {
    private final int mapperNumber;

    public UnsupportedMapperException(int mapperNumber) {
        this.mapperNumber = mapperNumber;
    }

    public int getMapperNumber() {
        return mapperNumber;
    }
}
