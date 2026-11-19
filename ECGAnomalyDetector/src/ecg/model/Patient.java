package ecg.model;

/**
 * Basic demographic information about the patient whose ECG is analysed.
 */
public class Patient {

    private final String patientId;
    private final String name;
    private final int age;
    private final String sex;

    public Patient(String patientId, String name, int age, String sex) {
        this.patientId = patientId;
        this.name = name;
        this.age = age;
        this.sex = sex;
    }

    public String getPatientId() { return patientId; }
    public String getName()      { return name; }
    public int getAge()          { return age; }
    public String getSex()       { return sex; }

    @Override
    public String toString() {
        return name + " (" + patientId + "), " + age + " y, " + sex;
    }
}

