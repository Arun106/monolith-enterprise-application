/*
 * |-------------------------------------------------
 * | Copyright © 2018 Colin But. All rights reserved.
 * |-------------------------------------------------
 */
package com.mycompany.entapp.snowman.domain;

import com.mycompany.entapp.snowman.domain.model.Employee;
import com.mycompany.entapp.snowman.domain.model.EmployeeRole;

public final class EmployeeTestHelper {

    private EmployeeTestHelper(){
    }

    public static Employee getEmployee() {
        Employee employee = new Employee();
        employee.setId(1);
        EmployeeRole employeeRole = new EmployeeRole();
        employeeRole.setRole("Role");
        employee.setRole(employeeRole);
        employee.setFirstname("FirstName");
        employee.setSurname("Surname");
        return employee;
    }
}
