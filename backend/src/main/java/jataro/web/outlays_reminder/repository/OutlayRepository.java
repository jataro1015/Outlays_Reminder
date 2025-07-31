package jataro.web.outlays_reminder.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jataro.web.outlays_reminder.entity.Outlay;

public interface OutlayRepository extends JpaRepository<Outlay, Integer>{
	
	@Query("SELECT o FROM Outlay o WHERE FORMATDATETIME(o.createdAt, 'yyyy-MM-dd') = :date")
	List<Outlay> findByCreatedAtDate(@Param("date") LocalDate date);
}
