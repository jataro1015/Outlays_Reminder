package jataro.web.outlays_reminder.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import jataro.web.outlays_reminder.entity.Outlay;

public interface OutlayRepository extends JpaRepository<Outlay, Integer>{
}
